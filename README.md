# Mensetu AI - 音声面接練習アプリ

新卒就活生向けの音声面接練習アプリケーション。AIコーチと音声で会話しながら面接練習ができます。

## 技術スタック

### Backend
- Java 17+ (本番環境ではJava 21を推奨)
- Spring Boot 3.x
- Spring AI (Ollama integration)
- PostgreSQL
- WebSocket

### Frontend
- Next.js 15 (App Router)
- React 19
- TypeScript
- Tailwind CSS
- Vitest (Unit Testing)
- Playwright (E2E Testing)

### AI Services (Local)
- Ollama (LLM)
- faster-whisper (STT)
- VOICEVOX (TTS)

## セットアップ

### 前提条件
- Docker & Docker Compose
- (オプション) VS Code + Dev Containers拡張

### 開発環境の起動

1. リポジトリをクローン
```bash
git clone <repository-url>
cd mensetu-ai-app
```

2. 環境変数ファイルを作成
```bash
cp .env.example .env
```

3. Docker Composeでサービスを起動（PostgreSQL / Ollama / faster-whisper / VOICEVOX）
```bash
docker compose up -d
```
Ollamaはデフォルトでは**CPU動作**。NVIDIA GPUを使う場合は `docker-compose.yml` の `ollama` サービスにあるコメントアウト済みの `deploy:` ブロックを有効化する。

4. Ollamaモデルをダウンロード（初回のみ / 8Bは実行に約5〜6GBのRAMが必要）
```bash
docker compose exec ollama ollama pull llama3.1:8b
# メモリが少ない環境では軽量モデルで代用できる（.env の OLLAMA_MODEL を合わせて変更）
# docker compose exec ollama ollama pull llama3.2:1b
```

5. DBスキーマはバックエンド起動時にFlywayが自動適用する（手動マイグレーション不要）。
   `.env` の `POSTGRES_PASSWORD` と `application.yml` のデフォルトは揃えてあるため、
   ホストで直接 `./gradlew bootRun` する場合も追加の環境変数設定は不要。

### VSCode Dev Containerを使用する場合

1. VS Codeで開く
2. コマンドパレット（Cmd/Ctrl+Shift+P）から「Dev Containers: Reopen in Container」を選択
3. コンテナが起動するまで待機

### Backend開発

```bash
cd backend
./gradlew bootRun
```

バックエンドは http://localhost:8080 で起動します。

### Frontend開発

```bash
cd frontend
npm install
npm run dev
```

フロントエンドは http://localhost:3000 で起動します。API/WebSocketの接続先は
`NEXT_PUBLIC_API_URL` / `NEXT_PUBLIC_WS_URL`（未設定なら `http://localhost:8080` / `ws://localhost:8080`）。

### 練習モードの動作確認手順

1. `docker compose up -d` → `docker compose exec ollama ollama pull llama3.1:8b`
2. `cd backend && ./gradlew bootRun`（Flywayがスキーマを作成）
3. `cd frontend && npm run dev`
4. http://localhost:3000 を開く
5. サイドバーの「＋ 会社を追加」で会社を作成 → 会社の「＋」で質問を追加
6. 質問をクリックすると練習セッションが開始し、WebSocketで接続される
7. マイクボタン（音声）または下部のテキスト入力で回答すると、コーチがストリーミングで応答する
8. 「終了する」で終了し、軽いサマリーが表示される

> VOICEVOX が起動していれば応答が音声再生される。STT（faster-whisper）はマイク入力時のみ使用。
> どちらかが落ちていてもテキストのやり取りは継続できる。

## テスト

### Backend
```bash
cd backend
./gradlew test
```

### Frontend
```bash
cd frontend
npm run test          # Vitest (ユニットテスト)
npm run test:e2e      # Playwright (E2Eテスト)
npm run lint          # ESLint
```

## Gitブランチ戦略

- `main`: 本番環境用ブランチ
- `develop`: 開発基軸ブランチ
- `feature/*`: 機能開発用ブランチ

### 開発フロー

1. `develop`から`feature`ブランチを作成
```bash
git checkout develop
git checkout -b feature/your-feature-name
```

2. 開発・コミット
```bash
git add .
git commit -m "feat: add your feature"
```

3. プルリクエストを作成して`develop`にマージ

## プロジェクト構造

```
.
├── backend/              # Spring Bootアプリケーション
│   ├── src/
│   │   ├── main/java/com/interviewapp/
│   │   └── test/
│   └── build.gradle
├── frontend/             # Next.jsアプリケーション
│   ├── app/              # App Router
│   ├── components/       # Reactコンポーネント
│   ├── lib/              # ユーティリティ
│   └── tests/            # テスト
├── docs/                 # ドキュメント
├── .devcontainer/        # Dev Container設定
└── docker-compose.yml    # Docker Compose設定
```

## ドキュメント

- [CLAUDE.md](./CLAUDE.md) - プロジェクト開発指針
- [DB/API設計](./docs/interview_app_db_api_design.md) - データベースとAPI仕様

## 開発フェーズ

現在は「練習モード」の実装に集中しています。詳細は[CLAUDE.md](./CLAUDE.md)を参照してください。

## ライセンス

Private
