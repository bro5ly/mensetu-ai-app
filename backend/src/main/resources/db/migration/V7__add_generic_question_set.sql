-- 汎用的な質問集: 会社に紐づかない練習・本番(一問一答形式)の対象として、既存の質問バンク
-- (question_bank_entries)の内容をそのまま練習可能な質問(interview_questions)として複製する。
-- サイドバーには「会社」セクションの上に専用のセクションとして表示する
-- (com.interviewapp.company.CompanyService/CompanyController参照)。
--
-- 質問バンク自体は企業リサーチの質問生成プロンプトの手本としても引き続き使うため
-- (QuestionBankService.sampleForPrompt)、テーブルは分けたまま複製する(以後のライブ同期はしない。
-- 質問バンクと同じくキュレーションした静的データという位置づけのため)。

ALTER TABLE companies ADD COLUMN is_generic BOOLEAN NOT NULL DEFAULT false;

-- 固定UUIDの特別な「会社」として1行だけ作る(interview_questions.company_id の NOT NULL 制約を
-- そのまま満たしつつ、既存の practice/mock の仕組み(質問チャット・一問一答本番・レポート等)を
-- 一切変更せずに再利用するための割り切り)。
INSERT INTO companies (id, name, overview, is_generic, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', '汎用的な質問', NULL, true, now(), now());

INSERT INTO interview_questions (id, company_id, question_text, internal_category, display_order, created_at)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001', question_text, internal_category,
       row_number() OVER (ORDER BY internal_category, question_text) - 1, now()
FROM question_bank_entries;
