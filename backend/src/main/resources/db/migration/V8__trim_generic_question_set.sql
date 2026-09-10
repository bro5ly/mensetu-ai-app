-- 汎用的な質問集から、会社ごとに回答内容が明確に異なる質問を除外する。
-- V7で質問バンクの全カテゴリを複製したが、MOTIVATION(志望動機)・REVERSE_QUESTION(逆質問)は
-- 「当社」を前提にした質問で、会社に紐づかない汎用的な練習には向かない
-- (旧 QuestionBankService.sampleGenericForMock が SELF_PR / EXPERIENCE / JOB_AXIS のみを
-- 対象にしていたのと同じ整理。CLAUDE.md「汎用的な質問集」参照)。
--
-- 会社の固定UUIDはV7で作成した「汎用的な質問」の1行。
DELETE FROM interview_questions
WHERE company_id = '00000000-0000-0000-0000-000000000001'
  AND internal_category IN ('MOTIVATION', 'REVERSE_QUESTION');
