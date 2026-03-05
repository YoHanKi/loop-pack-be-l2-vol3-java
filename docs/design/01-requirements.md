# 요구사항 명세서

## 문서 개요

**목적**: 브랜드/상품/좋아요/주문 도메인의 기능 요구사항을 명확히 하고, 시퀀스/클래스/ERD 산출이 가능하도록 정책과 예외 규칙을 확정한다.

**범위**:
- 대고객 API: `/api/v1` prefix
- 어드민 API: `/api-admin/v1` prefix
- 인증: 헤더 기반 (Customer: `X-Loopers-LoginId`, `X-Loopers-LoginPw` / Admin: `X-Loopers-Ldap`)
- 제외: 결제(Payment)

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
| 비관적 락 | Pessimistic Lock | 재고 차감 전 행(row) 잠금 (`SELECT ... FOR UPDATE`) | Exclusive Lock |
| 쿠폰 템플릿 | Coupon Template | 쿠폰 발급의 기준이 되는 템플릿 (종류/금액/수량/만료일 정의) | Coupon Definition |
| 쿠폰 | User Coupon | 특정 회원에게 발급된 쿠폰 인스턴스 | Member Coupon |
| 발급 수량 | Issued Quantity | 해당 쿠폰 템플릿으로 발급된 쿠폰 수 | Issued Count |
| 할인 금액 | Discount Amount | 쿠폰 적용으로 차감되는 금액 | Deduction Amount |
| 최종 결제 금액 | Final Amount | 주문 원금에서 할인 금액을 차감한 금액 | Final Price |

### 상태(Enum)

| 상태명 | 값 | 의미 | 허용 전이 |
|--------|-----|------|-----------|
| 대기 | PENDING | 주문 생성 완료, 결제 대기 | → CANCELED |
| 취소 | CANCELED | 주문 취소 완료 | (종료 상태) |
| 사용 가능 | AVAILABLE | 쿠폰 발급됨, 사용 가능 상태 | → USED |
| 사용됨 | USED | 쿠폰이 주문에 적용되어 사용 완료 | → AVAILABLE (주문 취소 시) |
| 만료됨 | EXPIRED | `expiredAt < now()` 동적 계산 — DB에 저장하지 않음 | (표시 전용) |

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
- **F-C07**: 로그인한 사용자가 여러 상품을 포함한 주문을 생성한다 (재고 차감, 스냅샷 저장, 쿠폰 적용 선택)
- **F-C08**: 로그인한 사용자가 자신의 주문 목록을 조회한다 (기간 필터, 페이징)
- **F-C09**: 로그인한 사용자가 자신의 특정 주문 상세를 조회한다 (스냅샷 포함)
- **F-C10**: 로그인한 사용자가 PENDING 상태의 주문을 취소한다 (재고 복구, 상태 전이, 쿠폰 복원)

#### 쿠폰
- **F-C11**: 로그인한 사용자가 특정 쿠폰 템플릿으로 쿠폰을 발급받는다 (중복 발급 불가, 수량 제한)
- **F-C12**: 로그인한 사용자가 자신이 보유한 쿠폰 목록을 조회한다 (만료 여부 동적 계산)
- **F-C13**: 주문 생성 시 보유 쿠폰을 적용하여 할인 금액을 차감한다

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

#### 쿠폰 관리
- **F-A13**: 어드민이 쿠폰 템플릿 목록을 조회한다 (페이징)
- **F-A14**: 어드민이 쿠폰 템플릿 상세를 조회한다
- **F-A15**: 어드민이 새 쿠폰 템플릿을 생성한다 (FIXED/RATE 타입, 수량/만료일/최소주문금액 설정)
- **F-A16**: 어드민이 쿠폰 템플릿 정보를 수정한다
- **F-A17**: 어드민이 쿠폰 템플릿을 삭제한다 (soft delete)
- **F-A18**: 어드민이 특정 쿠폰의 발급 내역을 조회한다 (페이징)

---

## 유스케이스 상세

### UC-C07: 주문 생성 (POST /api/v1/orders)

#### Main Flow
1. **요청**: 사용자가 `items: [{productId, quantity}]` 배열 + `userCouponId(nullable)`로 주문 요청
2. **인증**: `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 검증 → `user_id` 추출
3. **입력 검증**:
   - items 배열이 비어있지 않음
   - 각 quantity >= 1
   - 동일 productId가 여러 개 있으면 quantity 합산
4. **쿠폰 처리** (userCouponId 제공 시):
   - 쿠폰 유효성 검증: AVAILABLE 상태, 미만료, minOrderAmount 충족
   - 할인 금액 계산: FIXED → `min(value, originalAmount)`, RATE → `originalAmount × rate / 100`
   - 쿠폰 상태 변경: 조건부 UPDATE (`WHERE status='AVAILABLE'`) → rowsAffected == 0이면 409 Conflict
5. **재고 차감** (동시성 제어):
   - productId를 오름차순으로 정렬 (데드락 방지)
   - 각 상품에 대해 **비관적 락** 획득 후 재고 검증 및 차감:
     ```sql
     -- 1단계: 비관적 락 획득
     SELECT * FROM products WHERE id = :productId FOR UPDATE;
     -- 2단계: 재고 검증 (애플리케이션 레이어)
     -- stock_quantity < :quantity → 409 Conflict
     -- 3단계: 재고 차감
     UPDATE products SET stock_quantity = stock_quantity - :quantity WHERE id = :productId;
     ```
   - 재고 부족 → 전체 롤백 후 409 Conflict
6. **상품 존재 확인**: 재고 차감 전 productId로 조회
   - 존재하지 않으면 → 404 Not Found
7. **주문 저장**:
   - `orders` 테이블에 INSERT: `ref_member_id`, `order_id(UUID)`, `status=PENDING`, `discount_amount`, `ref_user_coupon_id`
8. **주문 항목 스냅샷 저장**:
   - `order_items` 테이블에 각 항목 INSERT:
     - `order_id`, `product_id(스냅샷)`, `product_name(스냅샷)`, `price(스냅샷)`, `quantity`
9. **응답**: 201 Created
   - `orderId`, `status`, `items` 배열, `originalAmount`, `discountAmount`, `finalAmount`

#### Alternate Flow
- **A1**: 동일 productId 중복
  - quantity를 합산하여 단일 항목으로 처리
  - 예: `[{productId:"P001", qty:2}, {productId:"P001", qty:3}]` → `{productId:"P001", qty:5}`
- **A2**: userCouponId 미제공
  - discountAmount = 0, finalAmount = originalAmount

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: items 배열 비어있음 또는 quantity < 1 → 400 Bad Request
- **E3**: product 존재하지 않음 또는 deleted → 404 Not Found
- **E4**: 재고 부족 → 409 Conflict, 전체 롤백
- **E5**: 쿠폰 만료/이미 사용됨/minOrderAmount 미충족 → 409 Conflict
- **E6**: 트랜잭션 중 DB 에러 → 500 Internal Server Error, 롤백

---

### UC-C10: 주문 취소 (PATCH /api/v1/orders/{orderId}/cancel)

#### Main Flow
1. **요청**: 사용자가 `orderId`로 취소 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **주문 조회**:
   - `orderId`로 주문 조회
   - 존재하지 않으면 → 404 Not Found
4. **소유권 확인**: `order.ref_member_id == user_id` 검증
   - 다르면 → 403 Forbidden
5. **상태 확인**: `status == PENDING` 확인
   - CANCELED 상태면 → Alternate Flow A1 (멱등 성공)
6. **상태 전이**: 같은 트랜잭션 내에서
   - `orders.status = CANCELED` UPDATE
7. **재고 복구**:
   - `order_items`의 각 항목에 대해:
     ```sql
     UPDATE products
     SET stock_quantity = stock_quantity + :quantity
     WHERE id = :productId;
     ```
   - 상품이 삭제되었어도 재고 복구 시도 (soft delete이므로 가능)
8. **응답**: 200 OK
   - `orderId`, `status=CANCELED`

#### Alternate Flow
- **A1**: 이미 CANCELED 상태
  - 멱등 처리: 200 OK 응답 (재고 복구 중복 실행하지 않음)

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: 주문 존재하지 않음 → 404 Not Found
- **E3**: 소유권 불일치 → 403 Forbidden
- **E4**: status != PENDING (이미 취소되지 않은 다른 상태) → 409 Conflict
- **E5**: 트랜잭션 중 DB 에러 → 500 Internal Server Error, 롤백

---

### UC-C11: 쿠폰 발급 (POST /api/v1/coupons/{couponId}/issue)

#### Main Flow
1. **요청**: 사용자가 `couponTemplateId`와 `memberId`로 쿠폰 발급 요청
2. **쿠폰 템플릿 조회** (비관적 락):
   - `SELECT ... FOR UPDATE`로 템플릿 행 잠금
   - 존재하지 않으면 → 404 Not Found
3. **유효성 검증**:
   - `expiredAt >= now()` 확인 → 만료 시 409 Conflict
   - `issuedQuantity < totalQuantity` 확인 → 수량 초과 시 409 Conflict
   - 동일 회원이 이미 발급받은 쿠폰 여부 확인 → 중복 시 409 Conflict
4. **발급 처리**:
   - `coupon_templates.issued_quantity` 증가
   - `user_coupons` 테이블에 INSERT: `ref_member_id`, `ref_coupon_template_id`, `status=AVAILABLE`
5. **응답**: 201 Created
   - `userCouponId`, `couponTemplateId`, `status`, `issuedAt`

#### Exception Flow
- **E1**: 쿠폰 템플릿 존재하지 않음 → 404 Not Found
- **E2**: 쿠폰 만료됨 → 409 Conflict
- **E3**: 발급 수량 초과 → 409 Conflict
- **E4**: 중복 발급 시도 → 409 Conflict

---

### UC-C12: 내 쿠폰 목록 조회 (GET /api/v1/users/me/coupons)

#### Main Flow
1. **요청**: 사용자가 `memberId`로 보유 쿠폰 목록 조회
2. **조회**:
   - `user_coupons` 테이블에서 `ref_member_id`로 필터링
   - JOIN `coupon_templates` ON `user_coupons.ref_coupon_template_id = coupon_templates.id`
3. **만료 상태 동적 계산**:
   - DB에는 `AVAILABLE`/`USED`만 저장
   - 응답 시: `status == AVAILABLE && template.expiredAt < now()` → `EXPIRED`로 변환
4. **응답**: 200 OK
   - 쿠폰 배열: `userCouponId`, `couponName`, `type`, `value`, `status(AVAILABLE/USED/EXPIRED)`, `expiredAt`

#### Alternate Flow
- **A1**: 보유 쿠폰 없음 → 빈 배열 반환

---

### UC-A15: 쿠폰 템플릿 생성 (POST /api-admin/v1/coupons)

#### Main Flow
1. **요청**: 어드민이 쿠폰 템플릿 생성 (name, type, value, totalQuantity, expiredAt, minOrderAmount?)
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증 → 실패 시 403 Forbidden
3. **입력 검증**:
   - `type` in [FIXED, RATE]
   - FIXED: value > 0, RATE: 0 < value <= 100
   - `totalQuantity` >= 1
   - `expiredAt` > now()
4. **저장**: `coupon_templates` 테이블에 INSERT (issuedQuantity = 0)
5. **응답**: 201 Created

#### Exception Flow
- **E1**: 인증 실패 → 403 Forbidden
- **E2**: 입력 검증 실패 → 400 Bad Request

---

### UC-A17: 쿠폰 템플릿 삭제 (DELETE /api-admin/v1/coupons/{couponId})

#### Main Flow
1. **요청**: 어드민이 쿠폰 템플릿 삭제
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
3. **조회**: `couponTemplateId`로 템플릿 조회 → 없으면 404 Not Found
4. **Soft Delete**: `deleted_at = NOW()` UPDATE
5. **응답**: 204 No Content

---

### UC-C04: 좋아요 추가 (POST /api/v1/products/{productId}/likes)

#### Main Flow
1. **요청**: 사용자가 `memberId`, `productId`로 좋아요 추가 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **상품 존재 확인**:
   - `productId`로 상품 조회
   - `deleted_at IS NULL` 확인
   - 존재하지 않으면 → 404 Not Found
4. **중복 확인**: `(ref_member_id, ref_product_id)` UNIQUE 제약
   - 이미 존재하면 → Alternate Flow A1
5. **좋아요 저장**:
   - `likes` 테이블에 INSERT: `ref_member_id`, `ref_product_id`, `created_at=NOW()`
6. **응답**: 200 OK (기존 또는 신규 좋아요 정보 반환)

#### Alternate Flow
- **A1**: 이미 좋아요 존재
  - 멱등 처리: 200 OK (INSERT 스킵, 기존 좋아요 반환)
  - UNIQUE 제약 위반 catch 후 재조회하여 성공 처리

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: 상품 존재하지 않음 또는 deleted → 404 Not Found
- **E3**: DB 에러 (UNIQUE 제약 외) → 500 Internal Server Error

---

### UC-C05: 좋아요 취소 (DELETE /api/v1/products/{productId}/likes)

#### Main Flow
1. **요청**: 사용자가 `memberId`, `productId`로 좋아요 취소 요청
2. **인증**: 헤더 검증 → `user_id` 추출
3. **상품 존재 확인**:
   - `productId`로 상품 조회
   - 존재하지 않으면 → 404 Not Found
4. **좋아요 삭제**:
   - `(ref_member_id, ref_product_id)` 조건으로 조회 후 삭제
5. **응답**: 204 No Content

#### Alternate Flow
- **A1**: 좋아요 존재하지 않음
  - 멱등 처리: 204 No Content (없어도 성공)

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized
- **E2**: 상품 존재하지 않음 → 404 Not Found
- **E3**: DB 에러 → 500 Internal Server Error

---

### UC-C06: 내 좋아요 목록 조회 (GET /api/v1/users/me/likes)

#### Main Flow
1. **요청**: 사용자가 자신의 좋아요 목록 조회
2. **인증**: 헤더 검증 → `user_id` 추출
3. **조회**:
   - `likes` 테이블에서 `ref_member_id`로 필터링
   - JOIN `products` ON `likes.ref_product_id = products.id`
   - `products.deleted_at IS NULL` 필터링 (삭제된 상품 제외)
4. **페이징**: page, size 파라미터 (선택)
5. **응답**: 200 OK
   - products 배열: `productId`, `productName`, `brandName`, `price`, `likedAt`

#### Alternate Flow
- **A1**: 좋아요한 상품이 없음
  - 빈 배열 반환

#### Exception Flow
- **E1**: 인증 실패 → 401 Unauthorized

---

### UC-C02: 상품 목록 조회 (GET /api/v1/products)

#### Main Flow
1. **요청**: 사용자가 상품 목록 조회 (쿼리 파라미터: `brandId`, `sort`, `page`, `size`)
2. **필터링**:
   - `deleted_at IS NULL` (삭제된 상품 제외)
   - `brandId` 제공 시: `products.ref_brand_id = :brandId`
3. **정렬**:
   - `latest` (기본): `updated_at DESC`
   - `price_asc`: `price ASC`
   - `likes_desc`:
     - LEFT JOIN likes, GROUP BY p.id, COUNT(l.id) DESC
4. **페이징**: page, size 적용 (기본: page=0, size=20)
5. **응답**: 200 OK
   - products 배열: `productId`, `productName`, `brandId`, `brandName`, `price`, `stockQuantity`, `likesCount`

#### Alternate Flow
- **A1**: 조건에 맞는 상품 없음
  - 빈 배열 반환

#### Exception Flow
- **E1**: 유효하지 않은 sort 값 → 400 Bad Request

---

### UC-C03: 상품 상세 조회 (GET /api/v1/products/{productId})

#### Main Flow
1. **요청**: 사용자가 `productId`로 상품 상세 조회
2. **조회**:
   - `productId`로 상품 조회
   - `deleted_at IS NULL` 확인
   - Brand 정보 조회 (ref_brand_id → brands 테이블)
   - 존재하지 않으면 → 404 Not Found
3. **좋아요 수 집계**:
   - `SELECT COUNT(*) FROM likes WHERE ref_product_id = :productId`
4. **응답**: 200 OK
   - `productId`, `productName`, `brandId`, `brandName`, `price`, `stockQuantity`, `likesCount`

#### Exception Flow
- **E1**: 상품 존재하지 않음 또는 deleted → 404 Not Found

---

### UC-A08: 상품 등록 (POST /api-admin/v1/products)

#### Main Flow
1. **요청**: 어드민이 상품 등록 (필드: `productId`, `brandId`, `productName`, `price`, `stockQuantity`)
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
   - 실패 시 → 403 Forbidden
3. **입력 검증**:
   - `price >= 0`, `stockQuantity >= 0`
   - `productName` 비어있지 않음
4. **브랜드 존재 확인**:
   - `brandId`로 브랜드 조회
   - `deleted_at IS NULL` 확인
   - 존재하지 않으면 → 404 Not Found
5. **상품 저장**: `products` 테이블에 INSERT
6. **응답**: 201 Created

#### Exception Flow
- **E1**: 인증 실패 → 403 Forbidden
- **E2**: 입력 검증 실패 → 400 Bad Request
- **E3**: 브랜드 존재하지 않음 → 404 Not Found

---

### UC-A09: 상품 수정 (PUT /api-admin/v1/products/{productId})

#### Main Flow
1. **요청**: 어드민이 상품 수정 (수정 가능: `productName`, `price`, `stockQuantity`)
2. **인증**: `X-Loopers-Ldap=loopers.admin` 검증
3. **상품 조회**:
   - `productId`로 조회
   - 존재하지 않으면 → 404 Not Found
4. **입력 검증**:
   - `price >= 0`, `stockQuantity >= 0`
   - **brandId 변경 시도 확인**: 요청에 brandId가 포함되어 있으면 → 400 Bad Request
5. **상품 수정**: Dirty Checking으로 UPDATE
6. **응답**: 200 OK

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
     - `UPDATE brands SET deleted_at = NOW() WHERE id = :brandId`
     - `UPDATE products SET deleted_at = NOW() WHERE ref_brand_id = :brandId AND deleted_at IS NULL`
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
  - 주문 취소: 이미 CANCELED면 성공 처리 (권장)

---

## 정합성/동시성 규칙

### 트랜잭션 경계
- **주문 생성**: 재고 차감 + 주문 저장 + 스냅샷 저장 → 단일 트랜잭션
- **주문 취소**: 상태 전이 + 재고 복구 → 단일 트랜잭션
- **브랜드 삭제**: 브랜드 soft delete + 상품 연쇄 soft delete → 단일 트랜잭션

### 동시성 제어 전략

| 도메인 | 경합 수준 | 중요도 | 전략 |
|--------|-----------|--------|------|
| 재고 (stock) | **높음** | **비즈니스 핵심** | **비관적 락** (`SELECT ... FOR UPDATE`) |
| 쿠폰 발급 (issue) | **높음** | **비즈니스 핵심** | **비관적 락** (`SELECT ... FOR UPDATE` on coupon_templates) |
| 쿠폰 사용 (use) | **높음** | **비즈니스 핵심** | **조건부 UPDATE** (`WHERE status='AVAILABLE'`, rowsAffected 체크) |
| 쿠폰 복원 (restore) | 낮음 | 정합성 | **조건부 UPDATE** (`WHERE status='USED'`, idempotent) |
| 좋아요 (like) | 낮음 | 참고 데이터 | DB UNIQUE 제약 (`uk_likes_member_product`) |

**재고 차감 (비관적 락)**:
- `SELECT ... FOR UPDATE`로 행 잠금 후 재고 검증 및 차감
- 데드락 방지: productId 오름차순 정렬로 락 획득 순서 고정
- 재고 부족 시: 예외 발생 → 트랜잭션 전체 롤백 → 409 Conflict

**좋아요 중복 방지 (DB 제약)**:
- UNIQUE 제약 (`ref_member_id`, `ref_product_id`)으로 DB 레벨 최종 방어
- 경합 발생 시: `DataIntegrityViolationException` catch → 기존 좋아요 재조회 → 멱등 성공

### 일관성 수준
- **강한 일관성**: 재고 수량 (과판매 절대 불가)
- **약한 일관성 허용**: 좋아요 수 집계 (Phase 2, 병목 시)

---

## 리스크 및 완화책

### Risk-01: Soft Delete 필터 누락
- **리스크**: 모든 조회 쿼리에 `deleted_at IS NULL` 필터 필요, 누락 시 삭제된 항목 노출
- **완화책**:
  - Repository Native Query에 `deleted_at IS NULL` 조건 포함
  - E2E 테스트에 삭제 시나리오 포함

### Risk-02: 좋아요 수 집계 성능
- **리스크**: `likes_desc` 정렬 시 COUNT 집계가 느려질 수 있음
- **완화책**:
  - Phase 1: LEFT JOIN + GROUP BY + COUNT (정확성 우선, 현재 구현)
  - Phase 2: `products.like_count` 컬럼 도입 (약한 일관성 허용)
  - 전환 시점: APM 모니터링으로 병목 관측 후 결정

### Risk-03: 재고 차감 데드락
- **리스크**: 다품목 주문 시 productId 순서가 다르면 비관적 락 데드락 가능
- **완화책**:
  - productId 오름차순 정렬로 락 순서 고정 (모든 트랜잭션이 동일한 순서로 락 획득)
  - DB 데드락 타임아웃 설정 (innodb_lock_wait_timeout)
  - 데드락 발생 시 자동 롤백 → 클라이언트 재시도

### Risk-04: 주문 스냅샷 불완전
- **리스크**: 스냅샷에 필수 정보 누락 시, 상품/브랜드 삭제 후 주문 조회 불가
- **완화책**:
  - 최소 스냅샷 필드: `product_id`, `product_name`, `price`, `quantity`
  - E2E 테스트: 상품 삭제 후 주문 조회 시나리오

### Risk-05: latest 정렬 조작 가능
- **리스크**: `updated_at` 기준 정렬 시, 운영자가 단순 수정으로 상단 노출 조작 가능
- **완화책**:
  - Phase 1: `updated_at` 사용 (단순함 우선)
  - Phase 2: `published_at` 또는 `last_restocked_at` 컬럼 도입

### Risk-06: 권한 검증 누락
- **리스크**: owner check 누락 시, 타 유저의 주문/좋아요 접근 가능
- **완화책**:
  - 모든 "내 리소스" API에 owner check 필수화
  - E2E 테스트: 타 유저 접근 시도 시나리오 (403 확인)

### Risk-07: 주문 취소 멱등성 불명확
- **리스크**: 이미 CANCELED 상태일 때 200 vs 409 정책 불명확
- **완화책**:
  - 명확한 정책: 멱등 성공 (200 OK) 채택
  - OrderModel.cancel()에서 이미 CANCELED면 그대로 반환 (예외 없음)

---

## Decision Log

### 1. 삭제 정책
- **결정**: Soft Delete 채택
- **근거**: 복구/감사 용이성, 주문 스냅샷과의 충돌 방지
- **구현**: `deleted_at` 컬럼 사용, 모든 조회에 `deleted_at IS NULL` 필터 적용
- **연쇄 삭제**: 브랜드 삭제 시 해당 브랜드의 모든 상품도 soft delete 처리

### 2. 정렬 기준
- **latest**: `updated_at DESC` (상품 갱신 반영)
- **price_asc**: `price ASC`
- **likes_desc**: LEFT JOIN likes, COUNT(l.id) DESC (Phase 1, 병목 시 like_count 컬럼 도입)

### 3. 좋아요 집계 정합성
- **기본안(Phase 1)**: 조회 시 COUNT 집계 (정확성 우선, 현재 구현)
- **확장안(Phase 2)**: `products.like_count` 컬럼 유지 (성능 우선, 약한 일관성 허용)
- **전환 시점**: 병목 관측 후 결정

### 4. 재고 차감 동시성 제어
- **방식**: 비관적 락 (`SELECT ... FOR UPDATE`)
- **근거**: 재고는 경합이 심하고 비즈니스적으로 중요한 자원. 과판매는 절대 허용 불가. 낙관적 접근(조건부 UPDATE)보다 명시적 락으로 직렬화 보장
- **데드락 방지**: productId 오름차순 정렬로 락 순서 고정
- **실패 처리**: 재고 부족 시 전체 롤백, 409 Conflict 응답
- **좋아요와의 차이**: 좋아요는 경합이 낮고 중복 1건은 치명적이지 않아 UNIQUE 제약만으로 충분

### 5. 주문 스냅샷 범위
- **최소 스냅샷**: `product_id(비즈니스 ID)`, `product_name`, `price`, `quantity`
- **제외**: brand_name, line_amount (총 금액은 getTotalPrice()로 계산), image_url

### 6. 주문 상태
- **PENDING**: 주문 생성 직후 (결제 전 상태)
- **CANCELED**: 주문 취소 완료
- **허용 전이**: `PENDING → CANCELED` (이번 범위에서는 결제 상태 제외)

### 7. 멱등성 정책
- **좋아요 추가(POST)**: 이미 존재해도 200 성공 (기존 좋아요 반환)
- **좋아요 취소(DELETE)**: 없어도 204 성공
- **주문 취소(PATCH)**: 이미 CANCELED면 성공 처리

### 8. 권한/접근 제어
- **내 좋아요 목록**: 본인만 조회 가능
- **내 주문 목록/상세**: 본인만 조회 가능 (isOwner() check 필수)
- **어드민 API**: `X-Loopers-Ldap=loopers.admin` 검증

---

## 다음 단계

이 요구사항을 기반으로 다음 산출물을 작성한다:
- **02-sequence-diagrams.md**: 주문 생성, 주문 취소, 좋아요 추가/취소, 상품 목록 조회 시퀀스
- **03-class-diagram.md**: Brand/Product/Like/Order/OrderItem 도메인 모델 및 레이어별 책임
- **04-erd.md**: ERD + 제약/인덱스 + 상태/삭제 정책
