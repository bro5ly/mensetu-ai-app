-- 音声面接練習アプリ 初期スキーマ
-- 設計: docs/interview_app_db_api_design.md

-- 会社
CREATE TABLE companies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    overview          TEXT,
    culture_keywords  TEXT[],
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- 選考フローのステップ
CREATE TABLE company_interview_steps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    step_order   INT NOT NULL,
    stage        VARCHAR(100) NOT NULL,
    description  TEXT
);

-- 口コミ要約
CREATE TABLE company_review_summaries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    summary     TEXT NOT NULL,
    source_url  VARCHAR(500)
);

-- 具体的な質問
CREATE TABLE interview_questions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    question_text      TEXT NOT NULL,
    internal_category  VARCHAR(50),
    display_order      INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- チャットセッション（練習 or 本番）
CREATE TABLE chat_sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    question_id   UUID REFERENCES interview_questions(id) ON DELETE SET NULL,
    mode          VARCHAR(10) NOT NULL CHECK (mode IN ('PRACTICE','MOCK')),
    status        VARCHAR(15) NOT NULL DEFAULT 'READY'
                  CHECK (status IN ('READY','IN_PROGRESS','ENDED')),
    ended_reason  VARCHAR(15) CHECK (ended_reason IN ('USER_ENDED','AI_JUDGED')),
    light_summary TEXT,
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
                  CHECK (message_type IN ('NORMAL','ADVICE')),
    sequence_no   INT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 本番模擬面接のレポート
CREATE TABLE mock_interview_reports (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                UUID NOT NULL UNIQUE REFERENCES chat_sessions(id) ON DELETE CASCADE,
    score                     INT NOT NULL,
    answer_tendency_analysis  TEXT NOT NULL,
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

CREATE INDEX idx_steps_company     ON company_interview_steps(company_id);
CREATE INDEX idx_reviews_company   ON company_review_summaries(company_id);
CREATE INDEX idx_questions_company ON interview_questions(company_id);
CREATE INDEX idx_sessions_company  ON chat_sessions(company_id);
CREATE INDEX idx_sessions_question ON chat_sessions(question_id);
CREATE INDEX idx_messages_session  ON chat_messages(session_id, sequence_no);
CREATE INDEX idx_feedbacks_report  ON mock_question_feedbacks(report_id, sequence_no);

-- 練習セッションは質問ごとに1つを使い回す（既存があれば再開する）ため部分ユニーク制約を張る
CREATE UNIQUE INDEX uq_practice_session_per_question
    ON chat_sessions(question_id) WHERE mode = 'PRACTICE';
