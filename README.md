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

3. Docker Composeでサービスを起動
```bash
docker compose up -d
```

4. Ollamaモデルをダウンロード（初回のみ）
```bash
docker exec -it mensetu-ai-app-ollama-1 ollama pull llama3.1:8b
```

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

フロントエンドは http://localhost:3000 で起動します。

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
