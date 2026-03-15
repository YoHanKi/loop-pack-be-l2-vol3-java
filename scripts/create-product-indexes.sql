-- =============================================================================
-- products 테이블 복합 인덱스 생성 스크립트
--
-- 목적: 상품 목록 조회 3개 쿼리 패턴에 대한 인덱스 적용
--   Q1 (likes_desc): ref_brand_id 필터 + like_count DESC 정렬 + updated_at 보조 정렬
--   Q2 (latest)    : ref_brand_id 필터 + updated_at DESC 정렬
--   Q3 (price_asc) : ref_brand_id 필터 + price ASC 정렬
--
-- 적용 방식:
--   로컬: ProductModel @Table(indexes) 어노테이션 → ddl-auto:create 시 자동 생성
--   운영: 이 스크립트로 수동 적용 (또는 Flyway 도입 시 마이그레이션으로 전환)
--
-- 실행 방법:
--   mysql -u application -p application loopers < scripts/create-product-indexes.sql
-- =============================================================================

-- Q1: likes_desc 대응
-- ref_brand_id 로 브랜드 필터 → like_count DESC 로 정렬 인덱스 → deleted_at IS NULL 필터 포함
CREATE INDEX idx_products_brand_like
    ON products (ref_brand_id, like_count DESC, deleted_at);

-- Q2: latest 대응
-- ref_brand_id 로 브랜드 필터 → updated_at DESC 로 정렬 인덱스 → deleted_at IS NULL 필터 포함
CREATE INDEX idx_products_brand_latest
    ON products (ref_brand_id, updated_at DESC, deleted_at);

-- Q3: price_asc 대응
-- ref_brand_id 로 브랜드 필터 → price ASC 로 정렬 인덱스 → deleted_at IS NULL 필터 포함
CREATE INDEX idx_products_brand_price
    ON products (ref_brand_id, price, deleted_at);

-- ---------------------------------------------------------------------------
-- 인덱스 생성 확인
-- ---------------------------------------------------------------------------
SHOW INDEX FROM products;
