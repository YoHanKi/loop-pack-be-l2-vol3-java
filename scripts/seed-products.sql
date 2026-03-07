-- =============================================================================
-- Volume 5 성능 테스트용 시드 데이터 (brands 10개 + products 100,000건)
--
-- 실행 조건:
--   - 애플리케이션을 local 프로파일로 기동 완료 후 실행 (ddl-auto: create로 테이블 생성 완료)
--   - 이미 데이터가 있으면 brands의 UNIQUE 제약으로 중복 삽입 차단됨
--
-- 실행 방법:
--   mysql -u application -p application loopers < scripts/seed-products.sql
--   또는 MySQL Workbench / IntelliJ Database 에서 직접 실행
--
-- 예상 실행 시간: 10~30초 (MySQL 8.x 기준, rewriteBatchedStatements=true 설정 시)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Step 1. 브랜드 10개 삽입
--   brand_id: BRD01 ~ BRD10  (VARCHAR(10) 제약)
--   brand_name: 다양한 브랜드명
-- ---------------------------------------------------------------------------
INSERT INTO brands (brand_id, brand_name, created_at, updated_at, deleted_at)
VALUES
    ('BRD01', 'Alpha Collection', NOW(), NOW(), NULL),
    ('BRD02', 'Beta Mode',        NOW(), NOW(), NULL),
    ('BRD03', 'Gamma Style',      NOW(), NOW(), NULL),
    ('BRD04', 'Delta Wear',       NOW(), NOW(), NULL),
    ('BRD05', 'Epsilon Labs',     NOW(), NOW(), NULL),
    ('BRD06', 'Zeta Boutique',    NOW(), NOW(), NULL),
    ('BRD07', 'Eta Edition',      NOW(), NOW(), NULL),
    ('BRD08', 'Theta Co',         NOW(), NOW(), NULL),
    ('BRD09', 'Iota Works',       NOW(), NOW(), NULL),
    ('BRD10', 'Kappa Brand',      NOW(), NOW(), NULL)
ON DUPLICATE KEY UPDATE brand_name = VALUES(brand_name);

-- ---------------------------------------------------------------------------
-- Step 2. 숫자 생성용 임시 테이블 (0 ~ 99999, 총 10만 행)
--   TEMPORARY TABLE을 CROSS JOIN에서 재참조하면 [HY000][1137] 발생.
--   → 각 자릿수를 인라인 서브쿼리(UNION ALL)로 대체하여 해결.
-- ---------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_numbers;
CREATE TEMPORARY TABLE tmp_numbers (n INT UNSIGNED NOT NULL PRIMARY KEY);

INSERT INTO tmp_numbers (n)
SELECT (a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000) AS n
FROM
    (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN
    (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN
    (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN
    (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d
CROSS JOIN
    (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e;

-- ---------------------------------------------------------------------------
-- Step 3. 상품 100,000건 삽입
--
-- [컬럼별 분포 전략]
--
--   product_id   : 'P' + 8자리 zero-padding (P00000001 ~ P00100000)
--                  VARCHAR(20) 제약 만족
--
--   ref_brand_id : n % 10 → 브랜드 10개에 균등 분포 (각 10,000건)
--
--   product_name : '{브랜드명} Item-{5자리번호}'
--                  브랜드별 상품명이 구분되어 필터 테스트에 유리
--
--   price        : ((n % 50) + 1) * 10000 → 10,000원 ~ 500,000원 (10,000원 단위 50단계)
--                  인덱스 정렬 테스트를 위해 넓은 범위 + 고른 분포
--
--   stock_quantity : n % 501 → 0 ~ 500 (균등 분포)
--
--   like_count   : 멱법칙(Power-law) 분포 — 실제 서비스와 유사한 롱테일 패턴
--                  - n % 100 <  60 → 0 ~ 49    (60%: 대부분 낮은 좋아요)
--                  - n % 100 <  85 → 50 ~ 999  (25%: 중간 좋아요)
--                  - n % 100 <  97 → 1000 ~4999(12%: 인기 상품)
--                  - n % 100 >= 97 → 5000 ~9999(3%: 최상위 인기)
--                  → likes_desc 정렬 시 인덱스 효과가 극명하게 드러나도록 설계
--
--   updated_at   : 최근 365일 내 분산 → 최신순 정렬 인덱스 검증
--   created_at   : 최근 730일 내 분산 (updated_at보다 항상 이전)
-- ---------------------------------------------------------------------------
INSERT INTO products
    (product_id, ref_brand_id, product_name, price, stock_quantity, like_count, created_at, updated_at, deleted_at)
SELECT
    CONCAT('P', LPAD(t.n + 1, 8, '0'))                             AS product_id,

    b.id                                                            AS ref_brand_id,

    CONCAT(b.brand_name, ' Item-', LPAD(FLOOR(t.n / 10) + 1, 5, '0'))
                                                                    AS product_name,

    ((t.n % 50) + 1) * 10000                                       AS price,

    t.n % 501                                                       AS stock_quantity,

    CASE
        WHEN t.n % 100 < 60 THEN (t.n * 17)  % 50
        WHEN t.n % 100 < 85 THEN (t.n * 13)  % 950  + 50
        WHEN t.n % 100 < 97 THEN (t.n * 11)  % 4000 + 1000
        ELSE                      (t.n *  7)  % 5000 + 5000
    END                                                             AS like_count,

    NOW() - INTERVAL (t.n % 730) DAY                               AS created_at,
    NOW() - INTERVAL (t.n % 365) DAY                               AS updated_at,

    NULL                                                            AS deleted_at

FROM tmp_numbers t
JOIN brands b
    ON b.brand_id = CONCAT('BRD', LPAD((t.n % 10) + 1, 2, '0'))
WHERE t.n < 100000;

DROP TEMPORARY TABLE IF EXISTS tmp_numbers;

-- ---------------------------------------------------------------------------
-- Step 4. 삽입 결과 확인
-- ---------------------------------------------------------------------------
SELECT '=== 삽입 결과 확인 ===' AS info;

SELECT COUNT(*) AS total_products FROM products;

SELECT
    b.brand_name,
    COUNT(p.id)       AS product_count,
    MIN(p.like_count) AS min_likes,
    MAX(p.like_count) AS max_likes,
    ROUND(AVG(p.like_count), 1) AS avg_likes,
    MIN(p.price)      AS min_price,
    MAX(p.price)      AS max_price
FROM products p
JOIN brands b ON b.id = p.ref_brand_id
GROUP BY b.id, b.brand_name
ORDER BY b.brand_name;

SELECT
    '0~49'      AS like_range, COUNT(*) AS cnt FROM products WHERE like_count < 50
UNION ALL SELECT '50~999',    COUNT(*) FROM products WHERE like_count BETWEEN 50 AND 999
UNION ALL SELECT '1000~4999', COUNT(*) FROM products WHERE like_count BETWEEN 1000 AND 4999
UNION ALL SELECT '5000~9999', COUNT(*) FROM products WHERE like_count >= 5000;