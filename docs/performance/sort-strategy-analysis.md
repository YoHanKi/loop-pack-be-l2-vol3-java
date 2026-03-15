# 좋아요 순 정렬 전략 비교 — 비정규화 vs 다차원 MaterializedView

- 분석 대상: `products.like_count` 정렬 전략
- 분석 일자: 2026-03-07 (개선: 2026-03-11)
- MySQL 버전: 8.x (네이티브 MV 미지원 → 별도 집계 테이블로 시뮬레이션)

---

## MV 설계 — product_like_stats

MySQL 8.x는 네이티브 Materialized View를 미지원하므로, 별도 집계 테이블로 동작 원리를 재현한다.

```sql
CREATE TABLE product_like_stats (
    product_id   BIGINT       NOT NULL,
    brand_id     BIGINT       NOT NULL,
    time_window  VARCHAR(10)  NOT NULL,   -- 'all' | '7d' | '1d'
    like_count   BIGINT       NOT NULL DEFAULT 0,
    refreshed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (product_id, time_window)
)
```

**설계 의도**: `(brand_id, time_window)` 조합을 단일 테이블에 저장함으로써
브랜드별 랭킹, 기간별 트렌딩, 브랜드 + 기간 조합 쿼리를 likes 테이블 재스캔 없이
인덱스만으로 처리할 수 있다.

---

## MV 갱신 전략 3종

| 전략 | 갱신 시점 | 정합성 | 쓰기 부하 | 구현 복잡도 |
|------|----------|--------|----------|------------|
| Sync | like INSERT와 동일 트랜잭션 | 즉시 | 이벤트마다 stats UPDATE | 낮음 |
| Async | like INSERT 후 별도 트랜잭션 | 수 ms~수 초 지연 | 분산 처리 | 중간 (Consumer 필요) |
| Batch | 주기적 전체 재집계 | 배치 주기만큼 지연 | 일괄 처리, 피크 분산 | 낮음 (Scheduler) |

### 불일치 구간 측정 결과 (TestContainers 환경)

| 전략 | 좋아요 2건 등록 후 stats.all | 집계값 |
|------|--------------------------|-------|
| Sync | 즉시 2 | 2 (완전 일치) |
| Async (갱신 전) | 0 (불일치 구간) | 2 |
| Async (갱신 후) | 2 (동기화) | 2 |
| Batch (배치 전) | 0 (stale) | 2 |
| Batch (배치 후) | 2 (동기화) | 2 |

---

## 다차원 쿼리 — 비정규화가 답할 수 없는 것

비정규화(`products.like_count`)는 단일 값이므로 다음 쿼리 패턴에 대응 불가:

| 쿼리 패턴 | 비정규화 | MV |
|----------|---------|-----|
| 브랜드 내 인기 순위 | products 스캔 + brand 조건 필터 | stats 단독 (brand_id 컬럼) |
| 7일 트렌딩 | likes 전체 스캔 + GROUP BY 불가피 | stats WHERE time_window='7d' |
| 브랜드 + 7일 조합 | likes 전체 스캔 + products JOIN | stats WHERE brand_id=? AND time_window='7d' |

### 순위 역전 시나리오 (시간 윈도우)

```
prodA: 오래된 좋아요 5개 (7일 밖)
prodB: 최근 좋아요 3개 (7일 이내)

전체 랭킹 (time_window='all'): prodA(5) > prodB(3)
7일 트렌딩  (time_window='7d'): prodB(3) > prodA(0) — 순위 역전
```

비정규화의 `like_count`로는 이 차이를 표현할 수 없다.

---

## 비정규화 vs 다차원 MV 비교

| 관점 | 비정규화 | 다차원 MV |
|------|---------|---------|
| 단순 per-product 집계 | 인덱스 직접 활용 | stats+products JOIN (미미한 오버헤드) |
| 브랜드별 랭킹 | products 스캔 필요 | stats 단독 쿼리 |
| 시간 윈도우 트렌딩 | likes 전체 스캔 불가피 | stats WHERE time_window 인덱스 |
| 즉시 정합성 | 항상 최신 (Sync와 동일) | 전략에 따라 지연 가능 |
| 동시성 안전 | 원자 UPDATE 보장 | Sync: ON DUPLICATE KEY UPDATE 원자 보장 |
| 복구 | DBA 수동 재집계 SQL | Batch 재실행으로 자동 복구 (멱등) |
| 데이터 증가 시 읽기 비용 | O(1) 인덱스 | O(1) 인덱스 (단순 집계와 동일) |
| 쓰기 비용 | 이벤트마다 products UPDATE | Sync: 동일 / Batch: 분산 |
| 구현 복잡도 | 낮음 | Sync: 낮음 / Batch: 낮음 / Async: 중간 |

---

## 최종 선택: 비정규화 유지 + MV 도입 트리거 기록

### 현재 선택: 비정규화 (`products.like_count`)

단순 per-product 좋아요 순 정렬이 요구사항의 전부인 경우,
비정규화가 가장 단순하고 즉시 정합성이 보장된다.

### MV 도입 트리거

아래 요구사항이 추가될 경우 `product_like_stats` 도입을 검토한다:

- **브랜드별 인기 상품** 기능 추가 → `WHERE brand_id = ?` 쿼리 필요
- **최근 N일 트렌딩** 기능 추가 → `time_window = '7d'` 쿼리 필요
- likes 테이블이 수억 건을 초과하여 실시간 GROUP BY 비용이 허용 불가할 때

### 동시성 정확도 (측정 결과)

| 전략 | 30 스레드 동시 좋아요 | stats.all |
|------|------------------|-----------|
| 비정규화 | 50 스레드 → like_count = 50 | N/A |
| MV Sync | 30 스레드 → stats.all = 30 | 정확 |
| MV Batch | 30 스레드 → stats.all = 0 (배치 전) / 30 (배치 후) | 배치 후 정확 |