-- 企業ソース(ユーザーが登録したURLをサーバーがfetchして本文抽出したもの)
-- 設計: docs/interview_app_db_api_design.md
-- 関連度検索はPostgres拡張(pg_trgm等)に頼らず、アプリケーション側(CompanySourceService)で
-- キーワードスコアリングして行う(H2の統合テストでも同じ挙動になるようにするため)。

CREATE TABLE company_sources (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    url          VARCHAR(2048) NOT NULL,
    title        VARCHAR(255),
    content      TEXT NOT NULL,
    fetched_at   TIMESTAMP NOT NULL DEFAULT now(),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_sources_company ON company_sources(company_id);
