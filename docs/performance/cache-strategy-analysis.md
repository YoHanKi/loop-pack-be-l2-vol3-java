# 캐싱 전략 비교 — 상품 도메인 5가지 전략

- 분석 대상: `getProduct` (상품 상세) / `getProducts` (상품 목록) / `updateProduct` (수정)
- Redis: TestContainers
- 분석 일자: 2026-03-07

---

## 전략별 구조 요약

| 전략 | 읽기 | 쓰기 | 제어권 |
|------|------|------|--------|
| Cache-Aside | MISS → DB → PUT → 반환 | DB 수정 + Evict | 애플리케이션 |
| Read-Through | @Cacheable (MISS면 AOP가 DB 조회 후 자동 캐싱) | @CacheEvict | Spring AOP |
| Write-Through | 항상 HIT (DB 수정 + 캐시 즉시 갱신) | DB + PUT 동시 | 애플리케이션 |
| Write-Behind | 항상 HIT (캐시 선기록) | 캐시 선기록 → 비동기 DB flush | 비동기 Consumer |
| Write-Around | 수정 후 첫 조회 MISS → DB | DB 수정 + Evict (재기록 없음) | 애플리케이션 |

---

## AS-IS — 전략 도입 전 (단순 DB 직접 조회)

```
getProduct  → 매 요청 DB 쿼리
getProducts → 매 요청 DB 쿼리 + 집계/정렬 연산
```

- 상품 목록 100,000건 기준: Full Table Scan + filesort 반복 발생
- 동일 조건 반복 조회 시 불필요한 DB 부하

---

## TO-BE — 전략별 측정 결과

### Cache-Aside (현재 상품 상세 `getProduct`)

| 시나리오 | 결과 |
|---------|------|
| 첫 조회 (MISS) | DB 쿼리 → Redis PUT → TTL 60s 설정 |
| 두 번째 조회 (HIT) | Redis에서 반환 (DB 쿼리 없음) |
| 수정 후 조회 | @CacheEvict → MISS → DB 최신값 반환 + 재적재 |
| Redis flush 후 | MISS → DB fallback → 재적재 (서비스 무중단) |
| 10 스레드 동시 MISS | 모두 정상 응답 / DB 쿼리 최대 10회 허용 (Stampede) |

### Read-Through (현재 상품 목록 `getProducts`)

| 시나리오 | 결과 |
|---------|------|
| TTL | `products::*` 키 30초 이하 확인 |
| 두 번째 조회 | 동일 결과 HIT 반환 |
| 브랜드 필터 키 분리 | `products::nike:*` / `products::adidas:*` 독립 캐시 |

### Write-Through (시뮬레이션)

| 시나리오 | 결과 |
|---------|------|
| 수정 후 첫 조회 | HIT — DB 쿼리 없음 |
| 첫 읽기 응답시간 | Write-Through(HIT) < Write-Around(MISS→DB) |
| 트랜잭션 롤백 시 | 캐시=200,000원 / DB=100,000원 → **불일치 발생** |

```
캐시 PUT (200,000원) → DB 롤백
getProduct: HIT → 200,000원 (잘못된 값)
DB 직접:         100,000원 (정상)
```

### Write-Behind (시뮬레이션)

| 시나리오 | 결과 |
|---------|------|
| flush 전 불일치 구간 | 캐시=200,000원 / DB=100,000원 |
| 정상 flush 후 | 캐시=200,000원 / DB=200,000원 (동기화) |
| Redis 장애 시 | 미flush 쓰기 영구 소실 위험 |

### Write-Around (현재 수정/삭제 `updateProduct`, `deleteProduct`)

| 시나리오 | 결과 |
|---------|------|
| 수정 후 캐시 상태 | Evict — 키 없음 (재기록 없음) |
| 다음 조회 | MISS → DB → 최신값 + 재적재 |
| 10회 연속 수정 | 캐시 갱신 비용 0 (쓰기 내내 키 없음) |

---

## 전략 비교

| 관점 | Cache-Aside | Read-Through | Write-Through | Write-Behind | Write-Around |
|------|:-----------:|:------------:|:-------------:|:------------:|:------------:|
| 수정 후 첫 읽기 | MISS→DB | MISS→DB | HIT (즉시) | HIT (즉시) | MISS→DB |
| 불일치 구간 | 없음 | 없음 | 롤백 시 발생 | flush 전 항상 | 없음 |
| Redis 장애 내성 | DB fallback | DB fallback | stale 잔류 | 데이터 소실 | DB fallback |
| Stampede 위험 | 있음 | 있음 | 없음 | 없음 | 없음 |
| 데이터 소실 위험 | 없음 | 없음 | 없음 | 있음 | 없음 |
| 구현 복잡도 | 낮음 | 매우 낮음 | 중간 | 높음 | 낮음 |

---

## 최종 선택

| 유스케이스 | 선택 전략 | 이유 |
|-----------|---------|------|
| 상품 상세 `getProduct` | **Cache-Aside** | 수동 제어, DB fallback 내성, TTL 60s |
| 상품 목록 `getProducts` | **Read-Through** | @Cacheable 선언적 처리, 필터 키 자동 분리 |
| 수정/삭제 `update`/`delete` | **Write-Around** | 불일치 없음, 단순, 수정 빈도 낮음 |

**Write-Through 도입 트리거**: 수정 직후 조회 빈도가 높은 워크로드 + @TransactionalEventListener(AFTER_COMMIT) 적용 가능한 경우

**Write-Behind 도입 트리거**: 손실 허용 데이터(조회수, 인기도 등) + Redis AOF + Kafka 인프라 보유 시
