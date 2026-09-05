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
- Docker & Docker Compose（Windowsは Docker Desktop、WSL2バックエンド推奨）
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
（PowerShellの場合: `Copy-Item .env.example .env`）

3. Docker Composeでサービスを起動（PostgreSQL / faster-whisper / VOICEVOX）
```bash
docker compose up -d
```
**Ollamaはこのcomposeの対象外**（`docker-compose.yml`の`ollama`サービスは既定では起動しないprofile扱い）。次のステップでOllamaを用意する。

4. Ollamaを用意する。CPU性能とGPUの有無に応じて2通りの方法がある。

**方法A: ホストにネイティブインストール（推奨・Mac / CPUが強いWindows機）**

Mac
```bash
brew install ollama
ollama serve
```
Windows

[ollama.com](https://ollama.com/download/windows) からインストーラーをダウンロードして実行する。
インストール後は常駐サービスとして自動起動する（タスクトレイに表示される）ので、
`ollama serve`を手動で叩く必要はない。起動していない場合はスタートメニューからOllamaを起動する。

IDE + フロント + バックエンド + 各コンテナと同時にDocker上でCPU動作させるとメモリ不足で
`llama-server ... signal: killed`（OOM kill）になりやすいため、ネイティブ起動を既定にしている。
ホストの全RAM（Macであれば追加でMetal GPU）が使え、CPU動作のコンテナより桁違いに速い。

**方法B: NVIDIA GPU搭載Windows機で、GPU対応のOllamaコンテナを使う**

CPUが非力でもNVIDIA GPU（例: RTX 3060）があれば、ネイティブ起動よりこちらの方が速い。
Docker DesktopがGPUパススルーに対応していれば（WSL2バックエンド＋最新NVIDIAドライバがあれば
追加設定は不要）、以下でGPU対応のOllamaコンテナを起動できる。
```bash
docker compose --profile ollama-gpu up -d
```
`ollama`コンテナはホストのポート11434に公開されるため、`.env`の`OLLAMA_BASE_URL`は
デフォルトの`http://localhost:11434`のままで到達できる（変更不要）。

どちらの方法でも、`.env`の`OLLAMA_BASE_URL`はホストで直接`./gradlew bootRun`する場合は
`http://localhost:11434`のまま、devcontainerを使う場合は`docker-compose.dev.yml`が既定で
`http://host.docker.internal:11434`に向ける（`host.docker.internal`はDocker Desktop for
Mac/Windowsのどちらでも解決でき、方法Bのコンテナにも到達できる）。

5. Ollamaモデルをダウンロード（初回のみ）

方法A（ネイティブ起動）の場合:
```bash
# 現在の開発機で使用しているモデル（約2GB）。会話・企業リサーチの要約・質問生成すべてで使用する。
ollama pull llama3.2:3b
# Docker上でCPU動作させる等メモリに余裕がない環境向けの軽量モデル（約1.3GB）。
# 使う場合は .env の OLLAMA_MODEL も llama3.2:1b に変更する。
# ollama pull llama3.2:1b
# GPUがあれば以下も可（約5〜6GB）
# ollama pull llama3.1:8b
# 日本語重視なら qwen2.5:3b も可
```

方法B（GPUコンテナ）の場合、`ollama pull`ではなくコンテナ内で実行する:
```bash
# RTX 3060（12GB VRAM）クラスなら8Bモデルが快適に動く。.envのOLLAMA_MODELも合わせて変更する。
docker compose --profile ollama-gpu exec ollama ollama pull llama3.1:8b
# 日本語重視なら qwen2.5:7b も可
```

6. DBスキーマはバックエンド起動時にFlywayが自動適用する（手動マイグレーション不要）。
   `.env` の `POSTGRES_PASSWORD` と `application.yml` のデフォルトは揃えてあるため、
   ホストで直接 `./gradlew bootRun` する場合も追加の環境変数設定は不要。

> **Windowsでクローンする場合の注意**: シェルスクリプト（`backend/gradlew`など）が
> Gitの改行コード変換（CRLF化）で壊れないよう、リポジトリの`.gitattributes`で
> 改行コードを固定している。クローンするだけで自動的に正しい改行コードになるため、
> 追加設定は不要（`core.autocrlf`を独自に上書きしていなければ問題ない）。

### VSCode Dev Containerを使用する場合

1. VS Codeで開く
2. コマンドパレット（Cmd/Ctrl+Shift+P）から「Dev Containers: Reopen in Container」を選択
3. コンテナが起動するまで待機

Windowsでは、リポジトリを`C:\...`側ではなくWSL2のLinuxファイルシステム内
（例: `~/projects/mensetu-ai-app`）にクローンしてから開くと、ファイルI/Oが大幅に速くなる。
Docker DesktopのWSL2統合を有効にしていれば、Mac同様`host.docker.internal`が解決される。

### Backend開発

```bash
cd backend
./gradlew bootRun
```
Windows（コマンドプロンプト/PowerShell、devcontainerを使わずホストで直接動かす場合）:
```powershell
cd backend
.\gradlew.bat bootRun
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

### 動作確認手順

1. `docker compose up -d`（PostgreSQL / faster-whisper / VOICEVOX）
2. ホストでOllamaを起動し（Macは`ollama serve`、Windowsはインストール後は常駐サービスとして自動起動）、`ollama pull llama3.2:3b`
3. `cd backend && ./gradlew bootRun`（Windowsは`.\gradlew.bat bootRun`。Flywayがスキーマを作成）
4. `cd frontend && npm run dev`
5. http://localhost:3000 を開く
6. サイドバーの「会社を追加」→ 会社名を入力 →「検索する」でAIが企業概要をまとめる → 内容を確認・編集して「決定」→「この内容から質問を作成する」→「この質問をリストに追加する」
   （会社の「＋」から質問を手動追加することもできる）
7. 質問をクリックすると練習セッションが開始し、WebSocketで接続される
8. マイクボタン（音声）または下部のテキスト入力で回答すると、コーチがストリーミングで応答する
9. 「終了する」で終了し、軽いサマリーが表示される

> VOICEVOX が起動していれば応答が音声再生される。STT（faster-whisper）はマイク入力時のみ使用。
> どちらかが落ちていてもテキストのやり取りは継続できる。

## テスト

### Backend
```bash
cd backend
./gradlew test
```
Windows: `cd backend` の後 `.\gradlew.bat test`

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
