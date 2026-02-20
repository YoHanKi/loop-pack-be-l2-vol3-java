# 요구사항 명세서

## 문서 개요

**목적**: 브랜드/상품/좋아요/주문 도메인의 기능 요구사항을 명확히 하고, 시퀀스/클래스/ERD 산출이 가능하도록 정책과 예외 규칙을 확정한다.

**범위**:
- 대고객 API: `/api/v1` prefix
- 어드민 API: `/api-admin/v1` prefix
- 인증: 헤더 기반 (Customer: `X-Loopers-LoginId`, `X-Loopers-LoginPw` / Admin: `X-Loopers-Ldap`)
- 제외: 결제(Payment), 쿠폰(Coupon), 회원 도메인(이미 구현됨)

---

## 유비쿼터스 언어

### 도메인 용어

| 한글 | 영문 | 정의 | 금지 동의어 |
|------|------|------|-------------|
| 브랜드 | Brand | 상품을 그룹화하는 제조사/브랜드 | Maker, Vendor |
| 상품 | Product | 판매 가능한 아이템 (재고 포함) | Item, Goods |
| 재고 수량 | Stock Quantity | 판매 가능한 상품 개수 | Inventory, Stock Count |
| 좋아요 | Like | 사용자-상품 간 관심 표시 | Favorite, Wish |
| 주문 | Order | 사용자의 구매 요청 (여러 상품 포함 가능) | Purchase, Request |
| 주문 항목 | Order Item | 주문 내 개별 상품 라인 | Order Line, Line Item |
| 스냅샷 | Snapshot | 주문 시점의 상품 정보 복사본 | Copy, Archive |
| 삭제 일시 | Deleted At | Soft Delete 타임스탬프 | Removed At |

### 상태(Enum)

| 상태명 | 값 | 의미 | 허용 전이 |
|--------|-----|------|-----------|
| 대기 | PENDING | 주문 생성 완료, 결제 대기 | → CANCELED |
| 취소 | CANCELED | 주문 취소 완료 | (종료 상태) |

### API 경로 규칙

- 대고객: `/api/v1/{domain}/{id?}/{action?}`
- 어드민: `/api-admin/v1/{domain}/{id?}/{action?}`
- 본인 리소스: `/api/v1/users/me/{resource}`

---

## 문제 상황 재해석

### 사용자 관점
사용자는 브랜드/상품을 탐색하고, 마음에 드는 상품에 좋아요를 남기며, 여러 상품을 한 번에 주문한다. 주문 내역은 나중에 상품 정보가 바뀌거나 삭제되어도 "주문 당시 기준"으로 다시 볼 수 있어야 한다.

**핵심 불편/위험**:
- 좋아요한 상품이 삭제되면 "좋아요 목록"에서 어떻게 보일까?
- 주문 후 상품 가격이 바뀌면, 내 주문 내역은 어떤 가격으로 표시될까?
- 동시에 여러 사람이 같은 상품을 주문하면 재고가 음수가 될 수 있지 않을까?

### 비즈니스 관점
좋아요는 랭킹/추천의 입력 데이터가 된다. 정확히 1~2건 오차는 치명적이지 않지만, 중복/왜곡은 신뢰도를 깨뜨린다. 주문은 재고를 소모시키는 핵심 트랜잭션이며, 동시 주문에서도 "과판매(재고 음수)"가 발생하면 안 된다. 브랜드/상품 삭제는 운영에서 발생 가능하며, 고객 노출에서 제거되어야 한다.

**보장해야 할 것**:
- 재고 일관성 (음수 방지)
- 주문 스냅샷 (이력 보존)
- 삭제 안전성 (연쇄 삭제, 복구 가능)

### 시스템 관점
인증은 간소화되어 헤더 기반이므로, "항상 owner check"가 설계로 강제되어야 한다. 좋아요/정렬/집계는 성능 병목이 될 수 있으므로 "초기 단순안 + 병목 시 확장안"을 분리해야 한다. 주문 생성은 검증-재고차감-주문저장-스냅샷저장을 일관된 트랜잭션 경계로 묶어야 한다.

**핵심 제약**:
- 트랜잭션 원자성 (주문 생성, 주문 취소)
- 동시성 제어 (재고 차감)
- 권한 검증 (본인 리소스만 접근)

---

## 기능 목록

### Customer API (인증 필수)

#### 브랜드/상품 조회
- **F-C01**: 로그인한 사용자가 브랜드 ID로 브랜드 정보를 조회한다
- **F-C02**: 로그인한 사용자가 상품 목록을 조회한다 (필터: brandId, 정렬: latest/price_asc/likes_desc, 페이징)
- **F-C03**: 로그인한 사용자가 상품 ID로 상품 상세 정보를 조회한다

#### 좋아요
- **F-C04**: 로그인한 사용자가 특정 상품에 좋아요를 추가한다 (멱등)
- **F-C05**: 로그인한 사용자가 특정 상품의 좋아요를 취소한다 (멱등)
- **F-C06**: 로그인한 사용자가 자신이 좋아요한 상품 목록을 조회한다 (타 유저 접근 불가)

#### 주문
- **F-C07**: 로그인한 사용자가 여러 상품을 포함한 주문을 생성한다 (재고 차감, 스냅샷 저장)
- **F-C08**: 로그인한 사용자가 자신의 주문 목록을 조회한다 (기간 필터, 페이징)
- **F-C09**: 로그인한 사용자가 자신의 특정 주문 상세를 조회한다 (스냅샷 포함)
- **F-C10**: 로그인한 사용자가 PENDING 상태의 주문을 취소한다 (재고 복구, 상태 전이)

### Admin API (어드민 권한 필수)

#### 브랜드 관리
- **F-A01**: 어드민이 브랜드 목록을 조회한다 (삭제된 항목 제외)
- **F-A02**: 어드민이 브랜드 상세를 조회한다
- **F-A03**: 어드민이 새 브랜드를 등록한다
- **F-A04**: 어드민이 브랜드 정보를 수정한다
- **F-A05**: 어드민이 브랜드를 삭제한다 (soft delete, 연쇄 삭제)

#### 상품 관리
- **F-A06**: 어드민이 상품 목록을 조회한다 (삭제된 항목 제외)
- **F-A07**: 어드민이 상품 상세를 조회한다
- **F-A08**: 어드민이 새 상품을 등록한다 (브랜드 존재 검증)
- **F-A09**: 어드민이 상품 정보를 수정한다 (brandId 변경 불가)
- **F-A10**: 어드민이 상품을 삭제한다 (soft delete)

#### 주문 조회
- **F-A11**: 어드민이 전체 주문 목록을 조회한다 (페이징)
- **F-A12**: 어드민이 특정 주문 상세를 조회한다

---

## 유스케이스 상세

### UC-C07: 주문 생성 (POST /api/v1/orders)

#### Main Flow
1. **요청**: 사용자가 `items: [{productId, quantity}]` 배열로 주문 요청
2. **인증**: `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 검증 → `user_id` 추출
3. **입력 검증**:
   - items 배열이 비어있지 않음
   - 각 quantity >= 1
   - 동일 productId가 여러 개 있으면 quantity 합산
4. **상품 존재 확인**: 각 productId에 대해
   - product 테이블에서 조회
   - `deleted_at IS NULL` 확인
   - 존재하지 않으면 → 404 Not Found
5. **재고 차감** (동시성 제어):
   - productId를 오름차순으로 정렬 (데드락 방지)
   - 각 상품에 대해 조건부 UPDATE 실행:
     ```sql
     UPDATE product
     SET stock_qty = stock_qty - :quantity, updated_at = NOW()
     WHERE id = :productId
       AND deleted_at IS NULL
       AND stock_qty >= :quantity;
     ```
   - affected rows = 0이면 재고 부족 → 전체 롤백 후 409 Conflict
6. **주문 저장**:
   - `orders` 테이블에 INSERT: `user_id`, `total_amount`, `status=PENDING`, `ordered_at=NOW()`
   - `total_amount` = Σ(unit_price × quantity)
7. **주문 항목 스냅샷 저장**:
   - `order_item` 테이블에 각 항목 INSERT:
     - `order_id`, `product_id`, `product_name`, `brand_id`, `brand_name`
     - `unit_price`, `quantity`, `line_amount` (= unit_price × quantity)
     - 선택: `image_url`
8. **응답**: 201 Created
   - `orderId`, `status`, `orderedAt`, `totalAmount`
   - `items` 배열 (스냅샷 포함)

#### Alternate Flow
- **A1**: 동일 productId 중복
  - quantity를 합산하여 단일 항목으로 처리
  - 예: `[{productId:1, qty:2}, {productId:1, qty:3}]` → `{productId:1, qty:5}`

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: items 배열 비어있음 또는 quantity < 1 → 400 Bad Request
- **E3**: product 존재하지 않음 또는 deleted → 404 Not Found
- **E4**: 재고 부족 → 409 Conflict, 전체 롤백
- **E5**: 트랜잭션 중 DB 에러 → 500 Internal Server Error, 롤백

---

### UC-C10: 주문 취소 (PATCH /api/v1/orders/{orderId}/cancel)

#### Main Flow
1. **요청**: 사용자가 `orderId`로 취소 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **주문 조회**:
   - `orderId`로 주문 조회
   - 존재하지 않으면 → 404 Not Found
4. **소유권 확인**: `order.user_id == user_id` 검증
   - 다르면 → 403 Forbidden
5. **상태 확인**: `status == PENDING` 확인
   - CANCELED 또는 다른 상태면 → 409 Conflict (Alternate Flow A1 참조)
6. **상태 전이**: 같은 트랜잭션 내에서
   - `orders.status = CANCELED`, `canceled_at = NOW()` UPDATE
7. **재고 복구**:
   - `order_item`의 각 항목에 대해:
     ```sql
     UPDATE product
     SET stock_qty = stock_qty + :quantity, updated_at = NOW()
     WHERE id = :productId;
     ```
   - 상품이 삭제되었어도 재고 복구 시도 (soft delete이므로 가능)
8. **응답**: 200 OK
   - `orderId`, `status=CANCELED`, `canceledAt`

#### Alternate Flow
- **A1**: 이미 CANCELED 상태
  - 멱등 처리: 200 OK 응답 (재고 복구는 중복 실행하지 않음)
  - 또는 409 Conflict (정책에 따라 선택, 권장은 200 OK)

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: 주문 존재하지 않음 → 404 Not Found
- **E3**: 소유권 불일치 → 403 Forbidden
- **E4**: status != PENDING (이미 취소되지 않은 다른 상태) → 409 Conflict
- **E5**: 트랜잭션 중 DB 에러 → 500 Internal Server Error, 롤백

---

### UC-C04: 좋아요 추가 (POST /api/v1/products/{productId}/likes)

#### Main Flow
1. **요청**: 사용자가 `productId`로 좋아요 추가 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **상품 존재 확인**:
   - `productId`로 상품 조회
   - `deleted_at IS NULL` 확인
   - 존재하지 않으면 → 404 Not Found
4. **중복 확인**: `(user_id, product_id)` UNIQUE 제약
   - 이미 존재하면 → Alternate Flow A1
5. **좋아요 저장**:
   - `like` 테이블에 INSERT: `user_id`, `product_id`, `created_at=NOW()`
6. **응답**: 201 Created (또는 204 No Content)

#### Alternate Flow
- **A1**: 이미 좋아요 존재
  - 멱등 처리: 200 OK 또는 204 No Content (INSERT 스킵)
  - UNIQUE 제약 위반 catch 후 성공 처리

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: 상품 존재하지 않음 또는 deleted → 404 Not Found
- **E3**: DB 에러 (UNIQUE 제약 외) → 500 Internal Server Error

---

### UC-C05: 좋아요 취소 (DELETE /api/v1/products/{productId}/likes)

#### Main Flow
1. **요청**: 사용자가 `productId`로 좋아요 취소 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **좋아요 삭제**:
   - `DELETE FROM like WHERE user_id = :userId AND product_id = :productId`
4. **응답**: 204 No Content

#### Alternate Flow
- **A1**: 좋아요 존재하지 않음
  - 멱등 처리: 204 No Content (affected rows = 0이어도 성공)

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: DB 에러 → 500 Internal Server Error

---

### UC-C06: 내 좋아요 목록 조회 (GET /api/v1/users/me/likes)

#### Main Flow
1. **요청**: 사용자가 자신의 좋아요 목록 조회
2. **인증**: 헤더 검증 → `user_id` 추출
3. **조회**:
   - `like` 테이블에서 `user_id`로 필터링
   - JOIN `product` ON `like.product_id = product.id`
   - `product.deleted_at IS NULL` 필터링 (삭제된 상품 제외)
4. **페이징**: page, size 파라미터 (선택)
5. **응답**: 200 OK
   - products 배열: `productId`, `productName`, `brandName`, `price`, `imageUrl`, `likedAt`

#### Alternate Flow
- **A1**: 좋아요한 상품이 없음
  - 빈 배열 반환

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized

---

### UC-C02: 상품 목록 조회 (GET /api/v1/products)

#### Main Flow
1. **요청**: 사용자가 상품 목록 조회 (쿼리 파라미터: `brandId`, `sort`, `page`, `size`)
2. **인증**: 헤더 검증 (선택: 비로그인도 허용 가능, 정책에 따라)
3. **필터링**:
   - `deleted_at IS NULL` (삭제된 상품 제외)
   - `brandId` 제공 시: `product.brand_id = :brandId`
4. **정렬**:
   - `latest` (필수): `updated_at DESC`
   - `price_asc` (선택): `price ASC`
   - `likes_desc` (선택):
     - Phase 1: `SELECT ..., (SELECT COUNT(*) FROM like WHERE product_id = product.id) AS like_count ORDER BY like_count DESC`
     - Phase 2 (병목 시): `product.like_count DESC`
5. **페이징**: page, size 적용 (기본: page=0, size=20)
6. **응답**: 200 OK
   - products 배열: `productId`, `productName`, `brandName`, `price`, `stockQty`, `imageUrl`, `likeCount`(선택)

#### Alternate Flow
- **A1**: 조건에 맞는 상품 없음
  - 빈 배열 반환

#### Exception Flow
- **E1**: 인증 실패 (인증 필수 정책인 경우) → 401 Unauthorized
- **E2**: 유효하지 않은 sort 값 → 400 Bad Request

---

### UC-C03: 상품 상세 조회 (GET /api/v1/products/{productId})

#### Main Flow
1. **요청**: 사용자가 `productId`로 상품 상세 조회
2. **인증**: 헤더 검증 (선택: 비로그인도 허용 가능)
3. **조회**:
   - `productId`로 상품 조회
   - `deleted_at IS NULL` 확인
   - JOIN `brand` ON `product.brand_id = brand.id`
   - 존재하지 않으면 → 404 Not Found
4. **좋아요 수 집계** (선택):
   - `SELECT COUNT(*) FROM like WHERE product_id = :productId`
5. **응답**: 200 OK
   - `productId`, `productName`, `brandId`, `brandName`, `price`, `stockQty`, `description`, `imageUrl`, `likeCount`

#### Exception Flow
- **E1**: 상품 존재하지 않음 또는 deleted → 404 Not Found

---

### UC-A08: 상품 등록 (POST /api-admin/v1/products)

#### Main Flow
1. **요청**: 어드민이 상품 등록 (필드: `productName`, `brandId`, `price`, `stockQty`, `description?`, `imageUrl?`, `status?`)
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
   - 실패 시 → 403 Forbidden
3. **입력 검증**:
   - `price >= 0`, `stockQty >= 0`
   - `productName` 비어있지 않음
4. **브랜드 존재 확인**:
   - `brandId`로 브랜드 조회
   - `deleted_at IS NULL` 확인
   - 존재하지 않으면 → 404 Not Found
5. **상품 저장**:
   - `product` 테이블에 INSERT
   - `status` 기본값: `ACTIVE` (정책에 따라)
6. **응답**: 201 Created
   - `productId`, `productName`, `brandId`, `price`, `stockQty`, ...

#### Exception Flow
- **E1**: 인증 실패 → 403 Forbidden
- **E2**: 입력 검증 실패 → 400 Bad Request
- **E3**: 브랜드 존재하지 않음 → 404 Not Found

---

### UC-A09: 상품 수정 (PUT /api-admin/v1/products/{productId})

#### Main Flow
1. **요청**: 어드민이 상품 수정 (수정 가능: `productName`, `price`, `stockQty`, `description`, `imageUrl`, `status`)
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
3. **상품 조회**:
   - `productId`로 조회
   - 존재하지 않으면 → 404 Not Found
4. **입력 검증**:
   - `price >= 0`, `stockQty >= 0`
   - **brandId 변경 시도 확인**: 요청에 brandId가 포함되어 있으면 → 400 Bad Request
5. **상품 수정**:
   - UPDATE `product` SET ... `updated_at = NOW()`
   - `stockQty`는 절대값 SET 방식 (운영 목적)
6. **응답**: 200 OK
   - 수정된 상품 정보

#### Exception Flow
- **E1**: 인증 실패 → 403 Forbidden
- **E2**: 입력 검증 실패 → 400 Bad Request
- **E3**: brandId 변경 시도 → 400 Bad Request
- **E4**: 상품 존재하지 않음 → 404 Not Found

---

### UC-A05: 브랜드 삭제 (DELETE /api-admin/v1/brands/{brandId})

#### Main Flow
1. **요청**: 어드민이 `brandId`로 브랜드 삭제
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
3. **브랜드 조회**:
   - `brandId`로 조회
   - 존재하지 않으면 → 404 Not Found
4. **연쇄 삭제** (Soft Delete):
   - 같은 트랜잭션 내에서:
     - `UPDATE brand SET deleted_at = NOW() WHERE id = :brandId`
     - `UPDATE product SET deleted_at = NOW() WHERE brand_id = :brandId AND deleted_at IS NULL`
5. **응답**: 204 No Content

#### Exception Flow
- **E1**: 인증 실패 → 403 Forbidden
- **E2**: 브랜드 존재하지 않음 → 404 Not Found
- **E3**: 트랜잭션 중 DB 에러 → 500 Internal Server Error, 롤백

---

## 예외 처리 규칙

### 공통 예외
- **401 Unauthorized**: 인증 헤더 누락 또는 검증 실패
- **403 Forbidden**: 권한 부족 (어드민 API, 소유권 불일치)
- **404 Not Found**: 리소스 존재하지 않음 (삭제된 항목 포함)
- **400 Bad Request**: 입력 검증 실패
- **500 Internal Server Error**: 예상치 못한 서버 에러, 트랜잭션 롤백

### 도메인 예외
- **409 Conflict**:
  - 재고 부족 (주문 생성)
  - 주문 상태 불일치 (주문 취소 시 status != PENDING)
- **멱등 성공**:
  - 좋아요 추가/취소: 이미 존재/없어도 성공
  - 주문 취소: 이미 CANCELED면 성공 (권장)

---

## 정합성/동시성 규칙

### 트랜잭션 경계
- **주문 생성**: 재고 차감 + 주문 저장 + 스냅샷 저장 → 단일 트랜잭션
- **주문 취소**: 상태 전이 + 재고 복구 → 단일 트랜잭션
- **브랜드 삭제**: 브랜드 soft delete + 상품 연쇄 soft delete → 단일 트랜잭션

### 동시성 제어
- **재고 차감**: 조건부 원자 UPDATE (`WHERE stock_qty >= :qty`)
- **데드락 방지**: productId 오름차순 정렬로 락 순서 고정
- **좋아요 중복**: UNIQUE 제약 (`user_id`, `product_id`)으로 DB 레벨 보장

### 일관성 수준
- **강한 일관성**: 재고 수량 (과판매 절대 불가)
- **약한 일관성 허용**: 좋아요 수 집계 (Phase 2, 병목 시)

---

## 리스크 및 완화책

### Risk-01: Soft Delete 필터 누락
- **리스크**: 모든 조회 쿼리에 `deleted_at IS NULL` 필터 필요, 누락 시 삭제된 항목 노출
- **증상**: 고객에게 삭제된 상품/브랜드가 보임, 주문 생성 시 삭제된 상품 선택 가능
- **완화책**:
  - Repository 기본 조건/전역 스코프 적용 (JPA `@Where`, QueryDSL BooleanExpression)
  - 코드 리뷰 체크리스트에 추가
  - E2E 테스트에 삭제 시나리오 포함

### Risk-02: 좋아요 수 집계 성능
- **리스크**: `likes_desc` 정렬 시 COUNT 집계가 느려질 수 있음 (상품 수 × 좋아요 수 증가 시)
- **증상**: 상품 목록 조회 API 응답 시간 증가 (1초 이상)
- **완화책**:
  - Phase 1: COUNT 집계 (정확성 우선)
  - Phase 2: `product.like_count` 컬럼 도입 (약한 일관성 허용)
  - 전환 시점: APM 모니터링으로 병목 관측 후 결정

### Risk-03: 재고 차감 데드락
- **리스크**: 다품목 주문 시 productId 순서가 다르면 데드락 발생 가능
- **증상**: 주문 생성 실패, DB 로그에 deadlock 감지
- **완화책**:
  - productId 오름차순 정렬로 락 순서 고정
  - 재시도 로직 (exponential backoff)
  - 모니터링: 데드락 발생 횟수 추적

### Risk-04: 주문 스냅샷 불완전
- **리스크**: 스냅샷에 필수 정보 누락 시, 상품/브랜드 삭제 후 주문 조회 불가
- **증상**: 주문 내역에 "알 수 없는 상품" 표시, 고객 불만
- **완화책**:
  - 최소 스냅샷 필드 명시: `product_name`, `brand_name`, `unit_price`, `quantity`
  - E2E 테스트: 상품 삭제 후 주문 조회 시나리오

### Risk-05: latest 정렬 조작 가능
- **리스크**: `updated_at` 기준 정렬 시, 운영자가 단순 수정으로 상단 노출 조작 가능
- **증상**: 특정 상품이 부당하게 상단 노출, 공정성 문제
- **완화책**:
  - Phase 1: `updated_at` 사용 (단순함 우선)
  - Phase 2: `published_at` 또는 `last_restocked_at` 컬럼 도입 (의미 있는 갱신만 반영)
  - 정책: 어드민 수정 시 경고 메시지 또는 별도 "노출 순서" 필드

### Risk-06: 권한 검증 누락
- **리스크**: owner check 누락 시, 타 유저의 주문/좋아요 접근 가능
- **증상**: 보안 취약점, 개인정보 노출
- **완화책**:
  - 모든 "내 리소스" API에 owner check 필수화
  - AOP 또는 Spring Security 필터로 공통화
  - E2E 테스트: 타 유저 접근 시도 시나리오 (403 확인)

### Risk-07: 주문 취소 멱등성 불명확
- **리스크**: 이미 CANCELED 상태일 때 200 vs 409 정책 불명확
- **증상**: 클라이언트 재시도 로직 혼란
- **완화책**:
  - 명확한 정책 수립: 멱등 성공 (200 OK) 권장
  - API 문서에 멱등성 명시
  - 클라이언트 가이드 제공

---

## Decision Log

### 1. 삭제 정책
- **결정**: Soft Delete 채택
- **근거**: 복구/감사 용이성, 주문 스냅샷과의 충돌 방지
- **구현**: `deleted_at` 컬럼 사용, 모든 조회에 `deleted_at IS NULL` 필터 적용
- **연쇄 삭제**: 브랜드 삭제 시 해당 브랜드의 모든 상품도 soft delete 처리

### 2. 정렬 기준
- **latest**: `updated_at DESC` (상품 갱신 반영, 추후 `published_at` 확장 가능)
- **price_asc**: `price ASC`
- **likes_desc**: 기본은 COUNT 집계, 병목 시 `like_count` 컬럼 도입

### 3. 좋아요 집계 정합성
- **기본안(Phase 1)**: 조회 시 COUNT 집계 (정확성 우선)
- **확장안(Phase 2)**: `product.like_count` 컬럼 유지 (성능 우선, 약한 일관성 허용)
- **전환 시점**: 병목 관측 후 결정

### 4. 재고 차감 동시성 제어
- **방식**: 조건부 원자 UPDATE (`UPDATE ... WHERE stock_qty >= :qty`)
- **데드락 완화**: productId 오름차순 정렬로 락 순서 고정
- **실패 처리**: 재고 부족 시 전체 롤백, 409 Conflict 응답

### 5. 주문 스냅샷 범위
- **최소 스냅샷**: `product_name`, `brand_name`, `unit_price`, `quantity`, `line_amount`
- **선택 필드**: `image_url`, `product_id`, `brand_id` (참조용)
- **제외**: 상품 상세 메타데이터 (description 등)

### 6. 주문 상태
- **PENDING**: 주문 생성 직후 (결제 전 상태)
- **CANCELED**: 주문 취소 완료
- **허용 전이**: `PENDING → CANCELED` (이번 범위에서는 결제 상태 제외)

### 7. 멱등성 정책
- **좋아요 추가(POST)**: 이미 존재해도 200/204 성공
- **좋아요 취소(DELETE)**: 없어도 204 성공
- **주문 취소(PATCH)**: 이미 CANCELED면 성공 처리 (권장)

### 8. 권한/접근 제어
- **내 좋아요 목록**: 본인만 조회 가능 (URI: `/api/v1/users/me/likes`)
- **내 주문 목록/상세**: 본인만 조회 가능 (owner check 필수)
- **어드민 API**: `X-Loopers-Ldap=loopers.admin` 검증

---

## 다음 단계

이 요구사항을 기반으로 다음 산출물을 작성한다:
- **02-sequence-diagrams.md**: 주문 생성, 주문 취소, 좋아요 추가/취소, 상품 목록 조회 시퀀스
- **03-class-diagram.md**: Brand/Product/Like/Order/OrderItem 도메인 모델 및 레이어별 책임
- **04-erd.md**: ERD + 제약/인덱스 + 상태/삭제 정책
