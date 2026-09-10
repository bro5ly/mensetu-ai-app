-- 本番模擬面接(MOCKモード)の実装に必要なスキーマ追加。
-- mock_interview_reports / mock_question_feedbacks は V1 で既にテーブル定義のみ存在していた
-- (Entity化されておらず未使用だった)ため、ここでは追加のテーブル・カラムのみを作る。

-- 本番セッションごとに生成される質問フロー(会社に紐づく静的な interview_questions とは別建て)。
-- 本番開始時にLLMで「自己紹介→志望動機→経験系×2→逆質問」のような5問程度のフローを
-- そのセッション専用に新規生成し、ここに保存する(CLAUDE.md参照)。
CREATE TABLE mock_session_questions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    question_text     TEXT NOT NULL,
    internal_category VARCHAR(50),
    display_order     INT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mock_session_questions_session ON mock_session_questions(session_id, display_order);

-- 本番セッションの進行状態。PRACTICEでは未使用(既定値のまま)。
-- current_question_order: 現在取り組んでいる質問の display_order(0始まり)
-- current_question_followups: その質問で既に行ったフォローアップ(深掘り)の回数
ALTER TABLE chat_sessions ADD COLUMN current_question_order INT NOT NULL DEFAULT 0;
ALTER TABLE chat_sessions ADD COLUMN current_question_followups INT NOT NULL DEFAULT 0;

-- 各メッセージがどの質問(mock_session_questions.display_order)への回答/深掘りだったかを記録する。
-- 本番終了後のレポート生成で、質問ごとの回答をまとめて渡すために使う。PRACTICEでは常にNULL。
ALTER TABLE chat_messages ADD COLUMN question_order INT;
