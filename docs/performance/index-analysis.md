# 상품 목록 조회 인덱스 성능 분석

- 분석 대상 테이블: `products`
- 데이터 규모: 100,000건 (brands 10개, 각 10,000건)
- MySQL 버전: 8.x
- 분석 일자: 2026-03-07

---

## AS-IS — 인덱스 적용 전

### 분석 쿼리

```sql
-- Q1: 브랜드 필터 + 좋아요 순 정렬 (likes_desc)
SELECT * FROM products
WHERE deleted_at IS NULL AND ref_brand_id = 1
ORDER BY like_count DESC, updated_at DESC
LIMIT 10;

-- Q2: 브랜드 필터 + 최신순 정렬 (latest)
SELECT * FROM products
WHERE deleted_at IS NULL AND ref_brand_id = 1
ORDER BY updated_at DESC
LIMIT 10;

-- Q3: 브랜드 필터 + 가격 오름차순 정렬 (price_asc)
SELECT * FROM products
WHERE deleted_at IS NULL AND ref_brand_id = 1
ORDER BY price ASC
LIMIT 10;
```

### EXPLAIN 결과

| 쿼리 | type | key | rows (예측) | Extra |
|------|------|-----|-------------|-------|
| Q1: likes_desc | ALL | NULL | 99,510 | Using where; Using filesort |
| Q2: latest     | ALL | NULL | 99,510 | Using where; Using filesort |
| Q3: price_asc  | ALL | NULL | 99,510 | Using where; Using filesort |

### 실행 계획 상세 (MySQL Visual Explain)

**Q1: likes_desc**
```
SEQ_SCAN   table: products  → rows: 100,000  cost: 35.8
FILTER     (ref_brand_id = 1) AND (deleted_at IS NULL)  → rows: 10,000  cost: 39.1
SORT       like_count DESC, updated_at DESC  → rows: 99,510  cost: 40.3
LIMIT      10  → total cost: 40.3
```

**Q2: latest**
```
SEQ_SCAN   table: products  → rows: 100,000  cost: 36.6
FILTER     (ref_brand_id = 1) AND (deleted_at IS NULL)  → rows: 10,000  cost: 39.9
SORT       updated_at DESC  → rows: 99,510  cost: 41.0
LIMIT      10  → total cost: 41.0
```

**Q3: price_asc**
```
SEQ_SCAN   table: products  → rows: 100,000  cost: 37.2
FILTER     (ref_brand_id = 1) AND (deleted_at IS NULL)  → rows: 10,000  cost: 40.8
SORT       price ASC  → rows: 99,510  cost: 42.3
LIMIT      10  → total cost: 42.3
```

### 문제점 분석

**type: ALL (Full Table Scan)**
- 인덱스를 전혀 사용하지 않고 100,000건 전체를 스캔
- 브랜드 필터(`ref_brand_id = 1`)가 WHERE에 있지만 인덱스가 없어 필터링이 스캔 후 적용됨

**key: NULL**
- 사용된 인덱스 없음

**Using filesort**
- 정렬 컬럼(`like_count`, `updated_at`, `price`)에 인덱스가 없어 **별도 정렬 연산** 발생
- 데이터가 늘어날수록 정렬 비용이 선형 증가

**병목 구조 요약**
```
전체 100,000건 스캔 → WHERE 필터(10,000건 추출) → 추출된 행 전체 정렬 → LIMIT 10
```
LIMIT 10이 있어도 정렬은 10,000건 전체에 수행됨. 인덱스가 있으면 정렬 없이 순서대로 읽고 멈출 수 있음.

---

## TO-BE — 인덱스 적용 후

- 분석 일자: 2026-03-07
- 적용 방식: `ProductModel @Table(indexes)` 어노테이션 (ddl-auto:create 자동 생성)

### 적용 인덱스

```sql
-- Q1 (likes_desc) 대응
idx_products_brand_like   : (ref_brand_id, like_count DESC, deleted_at)

-- Q2 (latest) 대응
idx_products_brand_latest : (ref_brand_id, updated_at DESC, deleted_at)

-- Q3 (price_asc) 대응
idx_products_brand_price  : (ref_brand_id, price, deleted_at)
```

### EXPLAIN 결과

| 쿼리 | type | key | rows (예측) | Extra |
|------|------|-----|-------------|-------|
| Q1: likes_desc | ref | idx_products_brand_like   | 18,962 | Using index condition; Using filesort |
| Q2: latest     | ref | idx_products_brand_latest | 18,962 | Using index condition |
| Q3: price_asc  | ref | idx_products_brand_price  | 18,962 | Using index condition |

### AS-IS vs TO-BE 비교

| 항목 | AS-IS | TO-BE |
|------|-------|-------|
| type | ALL (Full Table Scan) | ref (Index Scan) |
| key | NULL (인덱스 미사용) | 각 쿼리별 인덱스 사용 |
| rows 예측 | 99,510 | 18,962 (약 81% 감소) |
| Q2/Q3 Extra | Using where; Using filesort | Using index condition |
| Q1 Extra | Using where; Using filesort | Using index condition; **Using filesort 유지** |

### 분석

**개선된 점**
- `type: ALL → ref`: 인덱스를 통해 `ref_brand_id` 조건으로 대상 범위를 한정
- Q2(latest), Q3(price_asc): `Using filesort` 완전 제거 — 인덱스 순서로 읽어 정렬 연산 생략
- rows 99,510 → 18,962: 전체 스캔 제거, 브랜드 필터 효과로 스캔 범위 감소

**Q1 `Using filesort` 유지 원인**
```
인덱스: (ref_brand_id, like_count DESC, deleted_at)
쿼리:   ORDER BY like_count DESC, updated_at DESC
```
`updated_at DESC`가 인덱스에 없어 보조 정렬에서 filesort 발생.
완전 제거하려면 인덱스를 `(ref_brand_id, like_count DESC, updated_at DESC, deleted_at)`로 변경 필요.
단, likes_desc 쿼리에서 like_count 동률 상품이 실제로 많지 않으면 보조 정렬 빈도가 낮아 실용적 영향 제한적.

**rows 예측 18,962 (기대값 10,000보다 높음)**
MySQL 옵티마이저 통계 추정치. `deleted_at IS NULL` 선택도를 통계에 완전히 반영하지 못한 결과.
실제 스캔 행 수는 인덱스 조건으로 제한되므로 Full Table Scan 대비 성능 향상은 유효.
