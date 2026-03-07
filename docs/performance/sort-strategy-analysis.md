# 좋아요 순 정렬 전략 비교 — 비정규화 vs MaterializedView 시뮬레이션

- 분석 대상: `products.like_count` 정렬 전략
- 데이터 규모: 10건 (TestContainers MySQL), EXPLAIN 분석: 100,000건
- MySQL 버전: 8.x
- 분석 일자: 2026-03-07

---

## AS-IS — MaterializedView 시뮬레이션 방식

### 구조

```
좋아요 등록
  └── likes 테이블 INSERT (products 미갱신)

배치 갱신 (@Scheduled 주기)
  └── UPDATE products SET like_count = (SELECT COUNT(*) FROM likes WHERE ...)
```

### 측정 결과

| 항목 | 결과 |
|------|------|
| 즉시 정합성 | 배치 실행 전: `like_count = 0` / 실제 집계: 2 (stale) |
| 정렬 정확성 | 배치 전 순위 역전 발생 (prodB 2 likes → prodA 1 like 로 잘못 정렬) |
| 동시성 | likes INSERT 경합 없음 (50/50 성공) |
| 복구 | 배치 1회 실행으로 자동 복구 (멱등) |
| 쓰기 부하 | products UPDATE 없음 (배치 주기에만 발생) |

### 문제점

```
좋아요 2건 등록 (likes INSERT만)
  → products.like_count = 0  (stale)
  → 집계값              = 2  (실제)

정렬 결과: prodA(1) > prodB(0)  ← prodB의 2 likes 미반영
실제 기준: prodB(2) > prodA(1)
```

- 배치 주기(1분)만큼 순위가 틀림
- "방금 좋아요를 눌렀는데 내 상품 순위가 올라가지 않는다" → 사용자 신뢰성 문제

---

## TO-BE — 비정규화 방식 (현재 구현)

### 구조

```
좋아요 등록 (LikeService.addLike)
  ├── likes 테이블 INSERT
  └── products.like_count = like_count + 1  ← 동일 트랜잭션, 원자 UPDATE
```

### 측정 결과

| 항목 | 결과 |
|------|------|
| 즉시 정합성 | 등록 직후 `like_count = 1` / 집계값 = 1 (완전 일치) |
| 정렬 정확성 | 비정규화 정렬 순서 = 집계 JOIN 정렬 순서 (일치) |
| 동시성 | 50 스레드 동시 좋아요 → `like_count = 50` (정확) |
| 복구 | DBA 수동 재집계 SQL 실행 필요 |
| 쓰기 부하 | 모든 좋아요 이벤트에 products UPDATE 발생 |

### EXPLAIN 비교 (100,000건)

| 쿼리 방식 | type | key | Extra |
|----------|------|-----|-------|
| 비정규화 (`ORDER BY like_count`) | ref | idx_products_brand_like | Using index condition |
| 집계 JOIN (`LEFT JOIN COUNT(*)`) | ALL | NULL | Using filesort |

---

## AS-IS vs TO-BE 비교

| 관점 | AS-IS (MV 시뮬레이션) | TO-BE (비정규화) |
|------|----------------------|-----------------|
| 즉시 정합성 | 배치 주기만큼 지연 | 항상 최신 |
| 정렬 신뢰도 | 배치 전 stale | 즉시 반영 |
| 동시성 안전 | likes INSERT 경합 없음 | 원자 UPDATE 보장 |
| 복구 | 배치 실행으로 자동 복구 | 수동 패치 필요 |
| 쓰기 패턴 | 배치 주기에만 products UPDATE | 이벤트마다 products UPDATE |
| 읽기 성능 | Full Table Scan + 집계 JOIN | 인덱스 직접 활용 |
| 구현 복잡도 | 배치 Job 운영 필요 | 서비스 레이어에서 완결 |

### 성능 비교 (10건, TestContainers)

| 쿼리 방식 | 응답시간 |
|----------|---------|
| 비정규화 정렬 | **1.009ms** |
| 집계 JOIN 정렬 | **1.271ms** |
| 비율 | 집계 JOIN이 약 1.26배 느림 |

> 소규모에서는 차이가 작으나, 100,000건 EXPLAIN 기준 비정규화는 인덱스 스캔(rows≈18,962),
> 집계 JOIN은 Full Table Scan + 집계 연산으로 데이터 증가 시 격차가 기하급수적으로 커진다.

### 최종 선택: 비정규화

즉시 정합성·원자 동시성 보장, 인덱스 활용, 구현 단순성.
핫스팟 발생 시(초당 1,000회 이상): Redis INCR + 비동기 반영 검토.
