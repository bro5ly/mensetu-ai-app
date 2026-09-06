# CLAUDE.md

## プロジェクト概要

新卒就活生向けの音声面接練習アプリ。Next.jsフロントエンド＋Spring Boot/Spring AIバックエンドで、ローカルのOllama・faster-whisper・VOICEVOXを使い、音声で面接官と会話しながら練習できるようにする。ClaudeやChatGPTの音声会話UIをベースにした構成。

## 現在の開発フェーズ(最重要)

**練習モードに加え、企業リサーチ(会社追加ウィザード)を進める。**

- 対象:
  1. 練習モード: 1つの質問に対してAIがコーチとして会話するモード
  2. 企業リサーチ: 会社名 → Web検索 → AIが企業概要・面接傾向を要約 → 確認・編集 → AIが質問を一括生成、という会社追加ウィザード
- 企業リサーチのWeb検索は `WebSearchClient` インターフェースの背後に閉じ込める。実装は自己ホストのSearXNG(`SearXngWebSearchClient`、APIキー不要、`docker-compose.yml`の`searxng`サービス)。リサーチ結果は `companies.overview` に集約する(`company_interview_steps` / `company_review_summaries` テーブルは今は使わない)
- 対象外(今は着手しない): 本番モード、サイドバーのカテゴリ表示、レポート/採点機能
- 本番モードに関する依頼が来ても、既存コードを壊さないことを優先する。設計に影響する大きな変更が必要な場合は、実装前に必ず確認する

## 技術スタック

- Backend: Java 21 / Spring Boot 3.x / Spring AI (ChatClient, `@Tool`, `.entity()`)
- Frontend: Next.js (App Router) / TypeScript / React
- LLM: Ollama (**既定はDockerではなくホスト側でネイティブ起動**する運用。Mac: `brew install ollama && ollama serve`。Windows: [ollama.com](https://ollama.com/download/windows) からインストーラーで導入するとバックグラウンドサービスとして自動起動する(手動で`ollama serve`を叩く必要はない)。devcontainerからは `OLLAMA_BASE_URL=http://host.docker.internal:11434` でホストのOllamaに到達する(`docker-compose.dev.yml`が既定でこの値を設定する。`host.docker.internal`はDocker Desktop for Mac/Windowsのどちらでも解決できる)。日本語の自然さを優先し、既定モデルは `qwen3.5:4b`(アプリのデフォルト値としてコードに設定済み。`OLLAMA_MODEL`未指定時のフォールバック)。Docker上でCPU動作させる等メモリに余裕がない環境では `OLLAMA_MODEL` を `qwen3.5:2b`/`qwen3.5:0.8b` に落とす、GPUに余裕がある環境では `qwen3.5:9b` に上げる。**CPUが非力でNVIDIA GPUがあるWindows機**(例: RTX 3060)向けに、`docker-compose.yml`/`docker-compose.dev.yml`に`ollama`サービスをprofile(`ollama-gpu`)として用意している。`docker compose --profile ollama-gpu up -d`で起動でき、ホストのポート11434に公開されるためネイティブ起動と同じ`OLLAMA_BASE_URL`設定で到達できる。既定では起動しないので他環境の運用に影響しない。コンテナ内でモデルをpullする場合は `docker compose --profile ollama-gpu exec ollama ollama pull qwen3.5:4b` を実行する。devcontainerには`docker-outside-of-docker`機能(`.devcontainer/devcontainer.json`)を入れており、devcontainer内のターミナルからでも`docker`/`docker compose`コマンドでホストのDockerデーモンを操作してこのコンテナを起動できる)
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

- Backend起動: `./gradlew bootRun` (Windowsは `gradlew.bat bootRun` / PowerShellでは `.\gradlew.bat bootRun`)
- Backendテスト: `./gradlew test` (コミット前に必ず実行。Windowsは `gradlew.bat test`)
- Frontend起動: `npm run dev`
- Frontendビルド確認: `npm run build`
- Frontend Lint: `npm run lint` (コミット前に必ず実行)
- Frontendユニットテスト: `npm run test` (Vitest。コミット前に必ず実行)
- FrontendE2Eテスト: `npm run test:e2e` (Playwright。機能追加時に随時実行)
- ローカルサービス一括起動: `docker compose up -d` (faster-whisper / VOICEVOX / DB)。**Ollamaはこの対象外**なのでホスト側で別途起動しておくこと(Macは `ollama serve`、WindowsはOllamaインストーラーが常駐サービスとして自動起動するため通常は不要)

## アーキテクチャ上の決定事項(変更する場合は要相談)

- **音声ファイルは保存しない。** STTでテキスト化した結果のみをDBに保存する(`chat_messages`に音声用カラムは持たない)
- **練習モード**: AI側から会話を終了してはいけない。ユーザーが終了ボタンを押すまで継続する。ユーザーが回答に詰まったら、すぐに具体的なコツや回答例を提示する。終了時のスコアリングは不要(軽いサマリーのみ任意で可)
- **練習モードのメッセージ種別**: AIが「アドバイス・回答例」を出す場合は応答冒頭に`<<ADVICE>>`マーカーを付けさせ、バックエンドで検出して`chat_messages.message_type`に反映する(マーカーが無ければ`NORMAL`)。本番モードの終了検知(`<<INTERVIEW_END>>`)と同じマーカー方式に統一する。WebSocketでは`assistant_message_start`イベントの`messageType`でフロントに伝える
- **本番モード(後回し)**: 終了判定は「AIが十分と判断」または「ユーザー強制終了」の2系統。AI判断はレスポンス末尾に `<<INTERVIEW_END>>` マーカーを付ける方式で検知する(ツール呼び出しは使わない。ローカル小型モデルでの信頼性とストリーミングTTSとの相性を優先)。終了後にバックグラウンドで1回のLLM呼び出しにまとめてスコア・具体的フィードバック・傾向分析を生成する。質問ごとの並列評価は行わない
- **質問の並び**: サイドバーは会社の直下に具体的な質問がそのまま並ぶ。カテゴリ(自己PR/志望動機など)はUI上のフォルダとしては作らず、`interview_questions.internal_category` に内部タグとしてのみ持たせる
- **企業リサーチ**: Web検索は `WebSearchClient` 抽象の背後に閉じ込め、実装は自己ホストのSearXNG(`SearXngWebSearchClient`)。会社追加ウィザードはステートレスなAPI(`POST /api/companies/research`、`POST /api/companies/generate-questions`)で未保存の下書きを作り、最終ステップの `POST /api/companies`(`questions[]` 付き)で会社＋質問を一括作成する(途中でキャンセルしても孤児レコードを残さない)。LLMによる要約・質問生成は `CompanyResearchLlm` 抽象の背後に置く(`PracticeCoachLlm` と同じ方針)。Ollama呼び出しはSpring AIのChatClientではなく `com.interviewapp.llm.OllamaChatClient` (自前のWebClientラッパー、`/api/chat` を直接叩く)経由。理由: Qwen3系のようなハイブリッド思考モデルは既定でthinkingを行い、使用中のSpring AI(1.0.0-M5)が `think` フィールドを露出していないため空応答になる問題があり、それを回避するため。リサーチ・質問生成の呼び出しは非ストリーミングで、`options`(`num_predict`等)で出力トークン数を絞る。質問生成は会社概要だけでなく `question_bank_entries`(定番の質問パターン+回答の型、`QuestionBankService`がカテゴリごとにサンプリング)も手本として渡し、質問が会社概要の言い回しに引っ張られて過度に狭くなるのを防ぐ
- **企業リサーチの自動Web検索の安全策**: SearXNG経由で見つけたURLを`UrlContentFetcher`(Jsoup)で直接スクレイピングすることになるため、法的・倫理的リスクを下げる目的で複数の安全策を入れている。`RobotsTxtChecker`でrobots.txtの`Disallow`/`Crawl-delay`を尊重(取得失敗時はブロックしない)、`HostFetchThrottle`で同一ホストへの連続アクセスに最低間隔を強制、`CompanyResearchService`に自動検索全体のクールダウン(既定10秒)、fetch自体には`SOURCES_FETCH_TIMEOUT_MS`のタイムアウトを設定。自動追加するソース件数も`AUTO_SOURCE_LIMIT`(既定3件)で絞っている
- **練習モードのTTS遅延対策**: VOICEVOXの`audio_query`(軽い・テキスト解析)と`synthesis`(重い・音声合成)を分離し、文が確定した時点で`audio_query`だけ即座に並行実行(`audioQueryExecutor`)、`synthesis`は単一エンジンへの同時リクエストを避けるため単一スレッドで直列化(`synthesisExecutor`)する構成にしている(`PracticeWebSocketHandler`)。加えて`app.voicevox.speed-scale`(既定1.1倍)で読み上げ速度を上げ、`TtsCache`で同一文・同一話者の合成結果を再利用する。VOICEVOX Engine自体のネイティブストリーミング合成は2026年時点でまだ設計段階で未実装のため(`voicevox_engine` issue #1492)、WAVを1文単位で待って送る方式が現状の上限
- **練習モードのテキスト表示は音声に同期させる(AIの発話のみ)**: AIの発話テキストは、対応する文の音声再生が実際に始まったタイミングでチャット吹き出しに表示する(フロントの`usePracticeSession.ts`が文ごとに`pendingSentencesRef`に溜め、`audioQueuePlayer`の`enqueue`第2引数`onStart`コールバックで表示する)。**注意**: 表示先のメッセージIDは、音声到着時点(`onAssistantAudio`が呼ばれた瞬間)で`streamingIdRef.current`をクロージャに固定して使う。実際の再生開始(`onStart`発火)はそのずっと後になりうるため、再生時に`streamingIdRef.current`を読み直すと既に次ターン用にnull化/上書きされていて表示が失われるバグを一度踏んだ(音声は最後まで再生されるがテキストが途中で欠ける症状だった)。TTS合成に失敗して音声が来なかった文は`assistant_message_end`受信時にまとめて表示するフォールバックがある。吹き出し内には`streaming`中、本文がまだ空なら「生成中...」、本文が表示され始めたら「再生中」を出す(`MessageBubble.tsx`)。AIの応答はマイクボタン上のライブキャプションには出さない(同じ内容を2箇所に同時表示するのをやめた)
- **ライブキャプションUI(自分の発話のみ)**: `ChatPanel.tsx`のマイクボタン上に、文字起こし結果を即時(演出的な遅延なし)表示する(`data-testid="live-caption"`)。確定した文字起こし結果は既に全文確定しているため小分けに見せる意味が無く、速度優先でそのまま出す。AIの応答が始まったタイミング(`onAssistantStart`)でこのキャプションは消える。確定済みのやり取りは従来通り`MessageList`のチャット履歴に残る
- **録音中のリアルタイム部分文字起こし(本格的なストリーミングSTT)**: 録音停止(`end_turn`)を待たず、録音中も数秒おき(既定2秒間隔、フロントの`usePracticeSession.ts`の`setInterval`)にその時点までの音声バッファをプレビュー文字起こしし、マイクボタン上のキャプションを更新する。プロトコルはWSメッセージ`request_partial_transcript`(クライアント→サーバー)/`partial_transcript`(サーバー→クライアント、`WsProtocol`/`WsEventCodec`)。バックエンド`PracticeWebSocketHandler.handlePartialTranscriptRequest`は本番の`ATTR_AUDIO`バッファを**リセットせず**スナップショットを取って`sttClient.transcribe`にかけるため、録音自体は継続され`end_turn`時の本文字起こしに影響しない。同一セッションで前回のプレビュー処理がまだ完了していない場合は`AtomicBoolean`(`ATTR_PARTIAL_IN_PROGRESS`)で無視する(クライアント側は多重送信を気にせず一定間隔で送るだけでよい)ほか、専用の`partialTranscriptExecutor`(2スレッド)で実行しWSハンドラのメッセージ処理をブロックしない。フロントは録音停止後(`processing`中、確定結果`transcript`到着まで)も直前のプレビューを表示し続け、確定結果が届いたらそちらを優先表示することでキャプションが一瞬空白に戻るのを防いでいる。録音のたびに再文字起こしする分バックエンド負荷は増えるが、ユーザーが明示的にこの方式を選択した(簡易な「聞き取り中...」プレースホルダー案は不採用)
- **STTのハルシネーション対策**: `WhisperSttClient`は`vad_filter=true`を必ず付けてfaster-whisper-server(Speaches)を呼ぶ。無音/低音量区間をそのままWhisperに渡すと、学習データ由来の無関係なフレーズを延々と繰り返す既知の幻覚が起きるため、VAD(音声区間検出)で無音を除去してから文字起こしさせる
- **回答フレームワーク(STAR/PREP)**: `PracticePromptFactory`で、経験を聞く質問にはSTAR型(状況→課題→行動→結果)、自己PR・志望動機・逆質問にはPREP型(結論→理由→具体例→結論)をコツ・回答例の型として使うよう指示している(型の名前は言わず、型に沿った内容だけ自然に話す)
- **業界情報はデフォルトで企業概要に含める**: チャット中にボタンでオン/オフして都度検索する方式ではなく、企業リサーチ時に`companies.overview`へ業界動向も一緒に含める設計にした(検索クエリに「業界動向」を追加、`CompanyResearchPromptFactory`に業界動向を1〜2点含める指示)。理由: リアルタイム会話中のツール呼び出しは「ツール呼び出しを使わない」という既存方針に反し、TTS遅延対策で削った待ち時間を増やしてしまうため。既に確立した「検索→要約→保存→プロンプトに含める」パターンを再利用している
- **質問を切り替える際は前のセッションを必ず片付ける**: `usePracticeSession.ts`の`openQuestion`は、サイドバーで別の質問(または同じ質問の再オープン)をクリックしたときに呼ばれるが、開始前に必ず`teardownSocket()`(前のWebSocketを閉じる・`partialTranscript`ポーリングを止める・再生中の音声を止める)を呼び、`chatState`/`partialTranscript`を`idle`/空文字にリセットしてから新しいセッションを開始する。**注意**: これを怠ると、会話の途中(`chatState`が`processing`/`responding`/`recording`のまま)で別の質問に切り替えたときに、新しい画面がその残留状態を引き継いでマイクボタンが最初から`disabled`になり、何か別のイベントで偶然`idle`に戻るまで1回目のクリックが効かない(体感上「マイクボタンを2回押さないと録音/文字起こしが始まらない」)不具合を一度踏んだ。加えて前のWebSocketを閉じずに新しいものを繋ぐと、古いソケットのハンドラが新しいセッションと共有された`setChatState`/`setMessages`等を後から書き換えてしまう問題もあった
- **プロンプトは否定形の指示より肯定形の指示を優先する**: `PracticePromptFactory`/`CompanyResearchPromptFactory`のシステムプロンプトを、否定形の禁止指示(「〜してはいけない」「〜は書かない」「〜を避ける」)中心の書き方から、肯定形の指示(「〜します」「〜だけを書く」)中心の書き方に見直した。理由: LLM(特に本アプリで使っているような小型モデル)は否定命令文の処理が苦手で、禁止したい概念自体を先に処理してしまい、かえってその概念を出力してしまう("Pink Elephant Problem")ことが知られている。特に「以下にまとめます、のような前置きは書かない」のように悪い例の文言をそのまま引用する書き方は、その文言自体を模倣して出力するリスクを上げるため、悪い例の引用ではなく肯定形の指示(「1文字目から本文を書き始める」)に置き換えた。ADVICEマーカーの指示も、旧版は同じマーカー文字列を「付けてください/付けないでください」の2回登場させていたが、後者を「(通常時は)何も付けずそのまま話し始めてください」という肯定形に変え、マーカー文字列自体を1回しか見せないようにした(繰り返し提示するとその文字列が誤って出力される確率が上がるため)。良い例/悪い例のペア提示(few-shot)や出力例の提示は、小型モデルへの効果が高い技術として確認済みのため維持・強化している
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
- 企業リサーチ(会社追加ウィザード): 着手中。Web検索はSearXNG自己ホストで実装済み、`companies.overview` に集約、LLMで要約・質問生成
- 本番モード・レポート機能は未着手

## 今後追加したい機能(未着手・アイデアメモ)

ユーザーからの要望メモ。優先順位や設計は未確定、着手前に必ず相談する。

- **質問と回答が確定したらKeepに出力**: 練習チャットで固まった質問・回答のペアをGoogle Keepにエクスポートしたい。Google Keepには公式の一般公開APIが無い(Keep APIはGoogle Workspaceアカウント向けの制限付きAPI)ため、実現方法(Workspace API利用 or クリップボード/Markdownファイルへのエクスポートで代替 等)を先に調査する必要がある
- **ユーザー設定で汎用的な質問への回答・経験を登録**: 自己PRや志望動機など使い回せる経験をユーザープロフィールとして登録し、練習チャットのプロンプトに反映する。ユーザー/アカウントという概念が今のスキーマに無いため、まずその設計から必要
- **本番モードとそのフィードバック機能**: 既存の対象外項目(本番モード・レポート/採点)を実際に着手する回。DB設計は `docs/interview_app_db_api_design.md` の `mock_interview_reports` / `mock_question_feedbacks` に予定済みなので、そこから始められる

### 気づいている他のやるべきこと

- **既存会社の概要再生成の導線が無い**: 「ソースを管理」で複数URLを追加できるようになったが、それを使って会社概要を再生成→保存するボタン/APIがまだ無い(今は手動でPATCHするか、ステートレスな `/research` を都度呼ぶしかない)
- **devcontainerで `OLLAMA_MODEL` が転送されない**: `docker-compose.dev.yml` の `devcontainer` サービスの `environment` に `OLLAMA_MODEL` が無く、`.env` で上書きしても効かない。今は `application.yml` のデフォルト値自体を変える運用でしのいでいる
- **Spring AIのバージョンが古い(1.0.0-M5)**: 最新は2.0.0まで進んでいる。アップグレードすればOllamaの `think` フィールドをネイティブに使えるようになり、自前の `OllamaChatClient` が不要になる可能性があるが、破壊的変更を伴うため計画的に検証が必要
- **関連ソース選定ロジック(文字bi-gram)の精度**: 練習チャットでどのソースを参考にするか選ぶロジックが素朴な文字一致ベース。件数が増えてきたら埋め込みベースの検索に置き換える余地がある
- **質問バンクの拡充**: 現状はキュレーションした静的24件のみ。実際の練習で狭すぎる/偏っていると感じたら追加・調整する
- **VOICEVOX Engineのネイティブストリーミング合成待ち**: 実装されれば1文単位でのWAV待ちすら不要になり、さらに遅延を減らせる見込み(`voicevox_engine` issue #1492 が実装されたら追従を検討)
- **TTS失敗時のテキスト/音声同期のズレ**: 文単位のTTS合成が失敗すると、その文の音声だけ届かず、以降の文の音声と表示テキストの対応が1つずつズレる(最終的に全文は正しく表示されるが、音声との同期がその回だけ崩れる)。バイナリ音声フレームに文の対応関係を示すメタデータが無いのが原因。頻度が低ければ許容、気になるようならWSプロトコルの拡張を検討
- **練習モードのシステムプロンプトが長くなると小型モデルで劣化する可能性**: `PracticeTurnService.RELEVANT_SOURCE_LIMIT`(3件)×`PracticePromptFactory.SOURCE_CONTENT_LIMIT`(1500文字)で、関連ソースだけで最大4500文字(日本語で概ね2000〜3000トークン超)がシステムプロンプトに載る可能性があり、これに会社概要・会話履歴が加わると、小型モデルで指示追従が劣化し始めるとされる目安(数千トークン程度)に近づく。現状は`num_ctx`(8192)には収まるため動作はするが、体感で「指示を無視される」「関係ない話をする」等が増えるようなら、関連ソース件数や1件あたりの文字数を減らす方向で調整する
