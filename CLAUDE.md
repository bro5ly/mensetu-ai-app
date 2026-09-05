# CLAUDE.md

## プロジェクト概要

新卒就活生向けの音声面接練習アプリ。Next.jsフロントエンド＋Spring Boot/Spring AIバックエンドで、ローカルのOllama・faster-whisper・VOICEVOXを使い、音声で面接官と会話しながら練習できるようにする。ClaudeやChatGPTの音声会話UIをベースにした構成。

## 現在の開発フェーズ(最重要)

**練習モードに加え、企業リサーチ(会社追加ウィザード)を進める。**

- 対象:
  1. 練習モード: 1つの質問に対してAIがコーチとして会話するモード
  2. 企業リサーチ: 会社名 → Web検索 → AIが企業概要・面接傾向を要約 → 確認・編集 → AIが質問を一括生成、という会社追加ウィザード
- 企業リサーチのWeb検索は `WebSearchClient` インターフェース＋スタブ実装で進める。実プロバイダ(Tavily等)への接続は後日。リサーチ結果は `companies.overview` に集約する(`company_interview_steps` / `company_review_summaries` テーブルは今は使わない)
- 対象外(今は着手しない): 本番モード、サイドバーのカテゴリ表示、レポート/採点機能
- 本番モードに関する依頼が来ても、既存コードを壊さないことを優先する。設計に影響する大きな変更が必要な場合は、実装前に必ず確認する

## 技術スタック

- Backend: Java 21 / Spring Boot 3.x / Spring AI (ChatClient, `@Tool`, `.entity()`)
- Frontend: Next.js (App Router) / TypeScript / React
- LLM: Ollama (**Dockerではなくホスト側でネイティブ起動**する運用。`brew install ollama && ollama serve`。devcontainerからは `OLLAMA_BASE_URL=http://host.docker.internal:11434` でホストのOllamaに到達する(`docker-compose.dev.yml`が既定でこの値を設定する)。現在の開発機(Mac)では `llama3.2:3b` を使用。Docker上でCPU動作させる等メモリに余裕がない環境では `OLLAMA_MODEL` を `llama3.2:1b` に落とす、GPUがある環境では `llama3.1:8b` 等に上げる。日本語品質重視なら `qwen2.5:3b` も可)
- STT: faster-whisper (Dockerコンテナ、REST経由で呼び出す)
- TTS: VOICEVOX Engine (Dockerコンテナ、REST経由で呼び出す)
- DB: PostgreSQL (Docker Compose。開発初期はH2でも可、本実装はPostgres前提でスキーマを書く)
- リアルタイム通信: WebSocket (マイク音声の送信・AI応答テキスト/音声の受信)

## ディレクトリ構成

```
backend/
  src/main/java/com/interviewapp/
    question/    質問関連 (InterviewQuestion)
    session/     チャットセッション (練習/本番共通の基盤)
    practice/    練習モード固有のロジック
    chat/        WebSocketハンドラ、STT/TTS連携
frontend/
  app/                Next.js App Router
  components/
    chat/             チャットUI・録音ボタンなど
  lib/
    websocket.ts       WebSocketクライアント
docs/
  interview_app_db_api_design.md   DB/API設計書(参照用、@import対象)
```

DB/APIの詳細設計は `docs/interview_app_db_api_design.md` を作成済みなので、`docs/`配下に配置してから以下を参照すること。

@docs/interview_app_db_api_design.md

## ビルド・テストコマンド

- Backend起動: `./gradlew bootRun`
- Backendテスト: `./gradlew test` (コミット前に必ず実行)
- Frontend起動: `npm run dev`
- Frontendビルド確認: `npm run build`
- Frontend Lint: `npm run lint` (コミット前に必ず実行)
- Frontendユニットテスト: `npm run test` (Vitest。コミット前に必ず実行)
- FrontendE2Eテスト: `npm run test:e2e` (Playwright。機能追加時に随時実行)
- ローカルサービス一括起動: `docker compose up -d` (faster-whisper / VOICEVOX / DB)。**Ollamaはこの対象外**なのでホスト側で別途 `ollama serve` を起動しておくこと

## アーキテクチャ上の決定事項(変更する場合は要相談)

- **音声ファイルは保存しない。** STTでテキスト化した結果のみをDBに保存する(`chat_messages`に音声用カラムは持たない)
- **練習モード**: AI側から会話を終了してはいけない。ユーザーが終了ボタンを押すまで継続する。ユーザーが回答に詰まったら、すぐに具体的なコツや回答例を提示する。終了時のスコアリングは不要(軽いサマリーのみ任意で可)
- **練習モードのメッセージ種別**: AIが「アドバイス・回答例」を出す場合は応答冒頭に`<<ADVICE>>`マーカーを付けさせ、バックエンドで検出して`chat_messages.message_type`に反映する(マーカーが無ければ`NORMAL`)。本番モードの終了検知(`<<INTERVIEW_END>>`)と同じマーカー方式に統一する。WebSocketでは`assistant_message_start`イベントの`messageType`でフロントに伝える
- **本番モード(後回し)**: 終了判定は「AIが十分と判断」または「ユーザー強制終了」の2系統。AI判断はレスポンス末尾に `<<INTERVIEW_END>>` マーカーを付ける方式で検知する(ツール呼び出しは使わない。ローカル小型モデルでの信頼性とストリーミングTTSとの相性を優先)。終了後にバックグラウンドで1回のLLM呼び出しにまとめてスコア・具体的フィードバック・傾向分析を生成する。質問ごとの並列評価は行わない
- **質問の並び**: サイドバーは会社の直下に具体的な質問がそのまま並ぶ。カテゴリ(自己PR/志望動機など)はUI上のフォルダとしては作らず、`interview_questions.internal_category` に内部タグとしてのみ持たせる
- **企業リサーチ**: Web検索は `WebSearchClient` 抽象の背後に閉じ込め、既定はスタブ実装。会社追加ウィザードはステートレスなAPI(`POST /api/companies/research`、`POST /api/companies/generate-questions`)で未保存の下書きを作り、最終ステップの `POST /api/companies`(`questions[]` 付き)で会社＋質問を一括作成する(途中でキャンセルしても孤児レコードを残さない)。LLMによる要約・質問生成は `CompanyResearchLlm` 抽象の背後に置く(`PracticeCoachLlm` と同じ方針)。リサーチ・質問生成の呼び出しは非ストリーミングで、`OllamaOptions` で出力トークン数を絞る
- ローカル1GPU環境では `OLLAMA_NUM_PARALLEL` を上げてもVRAM不足でキューイングされるだけのことが多い。並列化を前提にした設計をしない

## コーディング規約

- Java: DTOは可能な限り `record` を使う。Controllerは薄く保ち、ビジネスロジックは `Service` に置く。インデントは4スペース
- Spring AIのツールは `@Tool` アノテーションで定義し、構造化出力が必要な場合は `.entity(Xxx.class)` を使う
- TypeScript: strict mode。コンポーネントは関数コンポーネント＋Hooksのみ。インデントは2スペース
- 命名: DBカラムはsnake_case、Java/TypeScriptはcamelCase(JPAのマッピングで変換する)

## テスト方針

- **Backend**: JUnit 5 + Mockitoでユニットテスト(Service層中心)。Testcontainers(`@ServiceConnection`)で実物のPostgresを使った統合テストを書く(H2での代替は不可、本番と挙動が変わるため)。Controllerは`@WebMvcTest`+MockMvcでスライステストにする
- **Frontend**: Vitest + React Testing Libraryでコンポーネント/フックのユニットテスト。Playwrightで主要フロー(1問の練習を開始→回答→終了まで)のE2Eテスト
- マーカー検出(`<<ADVICE>>`、`<<INTERVIEW_END>>`)のようなパース処理は、WebSocketハンドラから切り離した純粋関数/クラスとして実装し、単体テストしやすくする
- Ollama/faster-whisper/VOICEVOXへの実際のHTTP呼び出しはテストではモック/スタブに差し替える。実サービスに依存する統合テストは書かない(ローカル専用サービスでCI環境に存在しないため)
- 新しい機能を実装したら、同じタイミングで対応するテストも書く。最低限: Service層のユニットテスト1つ以上。分岐ロジック(マーカー検出、終了判定、メッセージ種別判定など)は正常系・異常系を個別にテストする

## 現在の状況

- 練習モード: REST API + WebSocket チャット(Ollama/STT/TTS)+ フロント実装まで完了。フロントUIは `ui-design/` のプロトタイプに合わせて刷新済み
- 企業リサーチ(会社追加ウィザード): 着手中。Web検索はスタブ実装、`companies.overview` に集約、LLMで要約・質問生成
- 本番モード・レポート機能は未着手
