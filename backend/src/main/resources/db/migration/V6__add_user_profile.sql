-- ユーザープロフィール(履歴書のような、練習・本番の質問生成/深掘りで参考にする候補者情報)。
-- アプリはローカル単一ユーザー前提でログイン概念を持たないため、複数ユーザーを想定した
-- 設計にはせず、常に「作成日時が最も古い1行」だけを使う(UserProfileService参照)。
CREATE TABLE user_profile (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_text TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);
