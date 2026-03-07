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

> 인덱스 적용 후 결과는 Commit 4 완료 후 이 섹션에 추가 예정.

### 적용 예정 인덱스

```sql
-- Q1 (likes_desc) 대응
idx_products_brand_like   : (ref_brand_id, like_count DESC, deleted_at)

-- Q2 (latest) 대응
idx_products_brand_latest : (ref_brand_id, updated_at DESC, deleted_at)

-- Q3 (price_asc) 대응
idx_products_brand_price  : (ref_brand_id, price, deleted_at)
```

### 기대 효과

| 쿼리 | AS-IS type | 기대 type | AS-IS rows | 기대 rows | 기대 Extra |
|------|-----------|----------|-----------|----------|-----------|
| Q1: likes_desc | ALL | range | 99,510 | ~10,000 | Using index condition |
| Q2: latest     | ALL | range | 99,510 | ~10,000 | Using index condition |
| Q3: price_asc  | ALL | range | 99,510 | ~10,000 | Using index condition |

- `Using filesort` 제거 → 인덱스 순서로 읽으면서 정렬 생략
- rows 예측값 100,000 → 10,000 수준으로 감소 (브랜드 필터 효과)
- LIMIT 10이면 실제로 10건만 읽고 중단 가능 (early termination)
