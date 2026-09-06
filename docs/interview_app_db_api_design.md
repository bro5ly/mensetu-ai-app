# 音声面接練習アプリ - DB設計・API設計

これまでの会話で決まった要件をもとにした設計です。

- サイドバーは「会社」直下に具体的な質問（練習チャット）が並び、会社ごとに1つ「本番模擬面接」がある
- 本番は複数回挑戦できる（スコア推移を見られるように）
- 練習モードは終了時にレポート不要（軽いサマリーのみ任意）。AIの応答は「通常の深掘り」と「アドバイス・回答例」の2種類をUI側で区別して表示する
- 本番モードは終了時に「点数・質問ごとの具体的フィードバック・回答の傾向分析」を生成
- 本番の集計は終了後にバックグラウンドで1回のLLM呼び出しにまとめて行う（並列評価はしない）
- 音声ファイルは保存しない。STTでテキスト化した結果のみを保持する（確定事項）
- ローカル前提。UUID主キーで書いていますが、H2/SQLiteで組む場合はUUIDをアプリ側で`UUID.randomUUID()`生成しTEXT型で保持すれば同じ構造で流用できます。

---

## 1. DB設計

### ER概要（テキスト）

```
companies 1─N company_interview_steps
companies 1─N company_review_summaries
companies 1─N interview_questions
companies 1─N chat_sessions（mode=MOCK のときは question_id が NULL）
interview_questions 1─N chat_sessions（mode=PRACTICE、通常は質問ごとに1つを使い回す）
chat_sessions 1─N chat_messages
chat_sessions 1─0..1 mock_interview_reports（mode=MOCK かつ終了後のみ）
mock_interview_reports 1─N mock_question_feedbacks
```

### DDL

```sql
-- 会社
CREATE TABLE companies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    overview          TEXT,                    -- 企業リサーチで取得した概要
    culture_keywords  TEXT[],                   -- 社風キーワード
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- 選考フローのステップ（企業リサーチ結果、確認・編集済みの内容を保存）
CREATE TABLE company_interview_steps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    step_order   INT NOT NULL,
    stage        VARCHAR(100) NOT NULL,        -- 例: 書類選考 / 一次面接 / 最終面接
    description  TEXT
);

-- 口コミ要約（原文は保持せず要約＋出典のみ保存）
CREATE TABLE company_review_summaries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    summary     TEXT NOT NULL,
    source_url  VARCHAR(500)
);

-- 具体的な質問（サイドバーで会社の直下に並ぶもの）
CREATE TABLE interview_questions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    question_text      TEXT NOT NULL,
    internal_category  VARCHAR(50),            -- SELF_PR / MOTIVATION / EXPERIENCE / REVERSE_QUESTION 等
                                                 -- UIにはフォルダとして出さず、傾向分析の集計軸として使う
    display_order      INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- チャットセッション（練習 or 本番）
CREATE TABLE chat_sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    question_id   UUID REFERENCES interview_questions(id) ON DELETE SET NULL, -- MOCKはNULL
    mode          VARCHAR(10) NOT NULL CHECK (mode IN ('PRACTICE','MOCK')),
    status        VARCHAR(15) NOT NULL DEFAULT 'READY'
                  CHECK (status IN ('READY','IN_PROGRESS','ENDED')),
    ended_reason  VARCHAR(15) CHECK (ended_reason IN ('USER_ENDED','AI_JUDGED')), -- MOCKのみ使用
    light_summary TEXT,                         -- PRACTICE用の軽いサマリー（任意）
    started_at    TIMESTAMP,
    ended_at      TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 会話メッセージ（音声は保存せず、STTでテキスト化した結果のみを保存）
CREATE TABLE chat_messages (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role          VARCHAR(10) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content       TEXT NOT NULL,
    message_type  VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                  CHECK (message_type IN ('NORMAL','ADVICE')), -- PRACTICEのASSISTANT応答のみ意味を持つ。それ以外は常にNORMAL
    sequence_no   INT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 本番模擬面接のレポート（本番1回＝1レコード。複数回挑戦できるので session ごとに作られる）
CREATE TABLE mock_interview_reports (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                UUID NOT NULL UNIQUE REFERENCES chat_sessions(id) ON DELETE CASCADE,
    score                     INT NOT NULL,
    answer_tendency_analysis  TEXT NOT NULL,     -- 話し方の癖・回答の一貫性などの総評
    created_at                TIMESTAMP NOT NULL DEFAULT now()
);

-- レポート内の質問ごとの具体的フィードバック
CREATE TABLE mock_question_feedbacks (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id            UUID NOT NULL REFERENCES mock_interview_reports(id) ON DELETE CASCADE,
    question_text        TEXT NOT NULL,
    user_answer_summary  TEXT,
    feedback             TEXT NOT NULL,
    sequence_no          INT NOT NULL
);

-- 質問バンク(会社に紐づかないグローバルな参照データ。実装は V3__add_question_bank.sql)
-- 定番の面接質問+「良い回答の型」を保持し、企業リサーチの質問生成が会社概要の言い回しだけに
-- 引っ張られて過度に狭くなるのを防ぐための手本として使う。現状はキュレーションした静的データを
-- マイグレーションで登録している(WebSearchClientが実プロバイダに接続されたら差し替え候補)。
CREATE TABLE question_bank_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    internal_category VARCHAR(50) NOT NULL,        -- interview_questions と同じ内部カテゴリ
    question_text     TEXT NOT NULL,
    answer_guidance   TEXT NOT NULL,                -- 回答例そのものではなく「型」の説明
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- インデックス
CREATE INDEX idx_question_bank_category ON question_bank_entries(internal_category);
CREATE INDEX idx_steps_company     ON company_interview_steps(company_id);
CREATE INDEX idx_reviews_company   ON company_review_summaries(company_id);
CREATE INDEX idx_questions_company ON interview_questions(company_id);
CREATE INDEX idx_sessions_company  ON chat_sessions(company_id);
CREATE INDEX idx_sessions_question ON chat_sessions(question_id);
CREATE INDEX idx_messages_session  ON chat_messages(session_id, sequence_no);
CREATE INDEX idx_feedbacks_report  ON mock_question_feedbacks(report_id, sequence_no);
```

### 補足：Spring AIのChatMemoryとの関係

Spring AIの`ChatMemory`（会話履歴管理）は、内部的に独自のメッセージストアを持てます。`chat_messages`テーブルをアプリの正とし、`ChatMemoryRepository`をこのテーブルに向けて実装すれば、Spring AI側の会話継続（`ChatMemory.CONVERSATION_ID`に`session_id`を渡す）と、UI表示用の履歴取得を同じテーブルで両立できます。

---

## 2. API設計

REST（CRUD・企業リサーチ・レポート取得）＋ WebSocket（リアルタイム音声/テキスト）の組み合わせです。

### 企業関連

| メソッド | パス                                    | 説明                                                                                        |
| -------- | --------------------------------------- | ------------------------------------------------------------------------------------------- |
| POST     | `/api/companies/research`               | 会社名(＋任意で現在の下書き・フィードバック)から Web 検索＋LLM要約で企業概要の下書きを返す（未保存）。Web 検索は `WebSearchClient` 抽象の背後の SearXNG(自己ホスト)実装。結果は `{ overview, sources }`（`sources` はユーザー提供＋自動検索で見つかった最終的なソース一覧） |
| POST     | `/api/companies/generate-questions`     | 確認済みの `{ name, overview }` から面接想定質問を 3 件生成して返す（未保存、`{ questions: string[] }`） |
| POST     | `/api/companies`                        | 会社を作成。`{ name, overview?, questions?: string[] }` を受け取り、`questions` があれば会社作成と同時に一括登録する（ウィザードの最終ステップ）  |
| GET      | `/api/companies`                        | 会社一覧取得（サイドバー表示用）                                                              |
| GET      | `/api/companies/{companyId}`            | 会社詳細（プロフィール＋質問一覧）取得                                                        |
| PATCH    | `/api/companies/{companyId}`            | 会社プロフィールを編集                                                                       |
| DELETE   | `/api/companies/{companyId}`            | 会社を削除                                                                                   |

> 会社追加ウィザードはステートレス設計。`/research`・`/generate-questions` は DB に保存せず下書きだけを返し、
> `POST /api/companies` で会社＋質問をまとめて作成する（途中でキャンセルしても孤児レコードを残さない）。
> リサーチ結果は `companies.overview` に集約し、`company_interview_steps` / `company_review_summaries` は現状未使用。

### 質問関連

| メソッド | パス                                   | 説明                      |
| -------- | -------------------------------------- | ------------------------- |
| GET      | `/api/companies/{companyId}/questions` | 質問一覧取得              |
| POST     | `/api/companies/{companyId}/questions` | 質問を手動追加            |
| PATCH    | `/api/questions/{questionId}`          | 質問文/内部カテゴリを編集 |
| DELETE   | `/api/questions/{questionId}`          | 質問を削除                |

### セッション関連

| メソッド | パス                                           | 説明                                                                                                                                                                                              |
| -------- | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| POST     | `/api/questions/{questionId}/practice-session` | 練習セッションを開始（既存があれば再開して返す）                                                                                                                                                  |
| POST     | `/api/companies/{companyId}/mock-sessions`     | 新しい本番セッションを開始（複数回挑戦可）                                                                                                                                                        |
| GET      | `/api/companies/{companyId}/mock-sessions`     | 過去の本番セッション一覧（スコア推移表示用、レポート要約付き）                                                                                                                                    |
| GET      | `/api/sessions/{sessionId}`                    | セッション詳細＋メッセージ履歴取得                                                                                                                                                                |
| POST     | `/api/sessions/{sessionId}/end`                | セッションを終了（ユーザー強制終了）。**PRACTICE**は同期的に軽いサマリー(`messageCount`, `lightSummary`)を200で返す。**MOCK**はレポート生成を非同期トリガーし202を返す(結果は`GET /report`で取得) |
| GET      | `/api/sessions/{sessionId}/report`             | 本番レポート取得（生成完了後のみ200、未完了は404 or `status: PENDING`）                                                                                                                           |

### WebSocket（音声・テキストのリアルタイムやり取り）

`wss://.../ws/sessions/{sessionId}`

**クライアント → サーバー**

| type                    | 内容                                                   |
| ----------------------- | ------------------------------------------------------ |
| `audio_chunk`（binary） | マイク音声チャンク                                     |
| `end_turn`              | ユーザー発話の終了合図（Push-to-talkのボタン離しなど） |
| `force_end`             | ユーザーによるセッション強制終了                       |

**サーバー → クライアント**

| type                              | 内容                                                                  |
| --------------------------------- | --------------------------------------------------------------------- |
| `transcript`                      | STT結果のテキスト（ユーザー発話の文字起こし）                         |
| `assistant_message_start`         | AI応答の開始通知。`messageType: NORMAL \| ADVICE` を含む（下記参照）  |
| `assistant_text_chunk`            | LLM応答のテキストストリーム（UI表示用、TTS再生と並行）                |
| `assistant_audio_chunk`（binary） | TTS生成音声チャンク                                                   |
| `assistant_message_end`           | AI応答の完了通知。UIはここで「処理中」表示を消し、吹き出しを確定する  |
| `session_ended`                   | セッション終了通知（`reason: AI_JUDGED \| USER_ENDED`）               |
| `report_ready`                    | （MOCKのみ）レポート生成完了通知。フロントは`GET /report`を叩きに行く |

**メッセージ種別（`NORMAL` / `ADVICE`）の判定方法**

練習モードでLLMがアドバイス・回答例を出す場合のみ、応答冒頭に`<<ADVICE>>`マーカーを付けさせる。バックエンドはストリーミング開始直後にこのマーカーの有無だけをチェックして`assistant_message_start`の`messageType`に載せ、マーカー自体は本文から取り除いてから`assistant_text_chunk`として流す。本番モードの終了判定（`<<INTERVIEW_END>>`）と同じ「マーカー方式」に統一している。

---

## 3. 未検討・次に詰めると良い点

- 会社削除時に進行中セッションがある場合の扱い（強制終了してから削除、など）
- 練習セッションの「最初からやり直す」導線（既存`chat_messages`をアーカイブして新規作成するか、上書きするか）
- テストでOllama/faster-whisper/VOICEVOXをどう扱うか（実サービスを叩くか、フェイクに差し替えるか）→ CLAUDE.mdのテスト方針を参照
