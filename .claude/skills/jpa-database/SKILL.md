---
name: jpa-database
description: JPA 관련 패턴 (BaseEntity, Converter, Repository, Soft Delete, Dirty Checking). JPA 코드 작성 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# JPA & Database 패턴

## BaseEntity

### 정의
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    @Getter
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    @Getter
    private LocalDateTime updatedAt;

    @Getter
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        guard();
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        guard();
        this.updatedAt = LocalDateTime.now();
    }

    protected void guard() {
        // 하위 클래스에서 오버라이드하여 엔티티 검증 수행
    }

    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public void restore() {
        this.deletedAt = null;
    }
}
```

### 특징
- **공통 필드**: id, createdAt, updatedAt, deletedAt 자동 관리
- **자동 감사**: `@CreatedDate`, `@LastModifiedDate`로 생성/수정 시간 자동 설정
- **Soft Delete**: `delete()` 메서드로 논리 삭제 (멱등성 보장)
- **복원 기능**: `restore()` 메서드로 삭제 취소
- **검증 훅**: `guard()` 메서드를 오버라이드하여 저장 전 검증 수행

### 사용 예시
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {
    // 필드 정의

    @Override
    protected void guard() {
        if (this.memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId는 필수입니다");
        }
        if (this.gender == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "gender는 필수입니다");
        }
    }
}
```

---

## JPA Converter

### 정의
Value Object를 DB 컬럼으로 변환하기 위한 컨버터

### 패턴
```java
@Converter(autoApply = false)
public class MemberIdConverter implements AttributeConverter<MemberId, String> {

    @Override
    public String convertToDatabaseColumn(MemberId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public MemberId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new MemberId(dbData);
    }
}
```

### 규칙
- **autoApply = false**: 명시적으로 `@Convert` 어노테이션 사용
- **null-safety**: null 체크 필수
- **양방향 변환**:
  - `convertToDatabaseColumn`: VO → DB 컬럼 (String, Integer 등)
  - `convertToEntityAttribute`: DB 컬럼 → VO

### Entity에서 사용
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;

    @Convert(converter = EmailConverter.class)
    @Column(length = 100)
    private Email email;

    @Convert(converter = BirthDateConverter.class)
    @Column(nullable = false)
    private BirthDate birthDate;

    @Convert(converter = NameConverter.class)
    @Column(nullable = false, length = 50)
    private Name name;
}
```

### Converter 예시 모음

#### EmailConverter
```java
@Converter(autoApply = false)
public class EmailConverter implements AttributeConverter<Email, String> {
    @Override
    public String convertToDatabaseColumn(Email attribute) {
        return attribute == null ? null : attribute.address();
    }

    @Override
    public Email convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Email(dbData);
    }
}
```

#### BirthDateConverter
```java
@Converter(autoApply = false)
public class BirthDateConverter implements AttributeConverter<BirthDate, LocalDate> {
    @Override
    public LocalDate convertToDatabaseColumn(BirthDate attribute) {
        return attribute == null ? null : attribute.date();
    }

    @Override
    public BirthDate convertToEntityAttribute(LocalDate dbData) {
        return dbData == null ? null : new BirthDate(dbData);
    }
}
```

---

## Repository 패턴

### 3계층 구조

```
Domain Repository Interface (domain layer)
    ↓ implements
RepositoryImpl (infrastructure layer)
    ↓ delegates
JpaRepository (infrastructure layer)
```

### 1. Domain Repository Interface
```java
public interface MemberRepository {
    MemberModel save(MemberModel member);
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
```

**특징**:
- 도메인 계층에 위치
- 도메인 용어 사용
- 기술 세부사항 숨김
- 구현체는 Infrastructure 계층

### 2. RepositoryImpl
```java
@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public MemberModel save(MemberModel member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public boolean existsByMemberId(MemberId memberId) {
        return memberJpaRepository.existsByMemberId(memberId);
    }

    @Override
    public Optional<MemberModel> findByMemberId(MemberId memberId) {
        return memberJpaRepository.findByMemberId(memberId);
    }
}
```

**특징**:
- Infrastructure 계층에 위치
- Domain Repository 구현
- JpaRepository에 위임
- `@Component`로 Bean 등록

### 3. JpaRepository
```java
public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
```

**특징**:
- Infrastructure 계층에 위치
- Spring Data JPA 인터페이스 상속
- 쿼리 메서드 정의
- 기본 CRUD 제공 (save, findById, delete 등)

---

## Soft Delete 패턴

### 정의
물리적 삭제 대신 `deletedAt` 필드에 삭제 시간을 기록하여 논리적으로 삭제 처리

### BaseEntity의 delete/restore
```java
public void delete() {
    if (this.deletedAt == null) {
        this.deletedAt = LocalDateTime.now();
    }
}

public void restore() {
    this.deletedAt = null;
}
```

**멱등성 보장**:
- `delete()`: 이미 삭제된 경우 중복 처리하지 않음
- `restore()`: 이미 복원된 경우 null 유지

### 조회 시 삭제 필터링
```java
@Query("SELECT m FROM MemberModel m WHERE m.memberId = :memberId AND m.deletedAt IS NULL")
Optional<MemberModel> findByMemberId(@Param("memberId") MemberId memberId);
```

---

## JPA Dirty Checking

### 정의
영속성 컨텍스트가 관리하는 엔티티의 변경 사항을 자동으로 감지하여 트랜잭션 커밋 시 UPDATE 쿼리 자동 실행

### 사용 예시
```java
@Transactional
public void changePassword(String loginId, String loginPw,
                           String currentPassword, String newPassword) {
    MemberModel member = memberReader.getOrThrow(loginId);
    member.matchesPassword(passwordHasher, loginPw);
    member.changePassword(currentPassword, newPassword, passwordHasher);
    // 명시적 save() 없이 트랜잭션 커밋 시 자동 UPDATE
}
```

### 규칙
- **트랜잭션 필수**: `@Transactional` 없으면 동작하지 않음
- **영속 상태**: `findById()` 등으로 조회한 엔티티만 해당
- **명시적 save() 불필요**: 변경 감지 후 자동 UPDATE
- **성능 최적화**: 실제 변경된 필드만 UPDATE

---

## 트랜잭션 관리

### Service 계층에서 관리
```java
@Service
@RequiredArgsConstructor
public class MemberService {

    @Transactional
    public MemberModel register(/* ... */) {
        // 쓰기 작업
        MemberModel member = MemberModel.create(/* ... */);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String loginPw) {
        // 읽기 전용 작업
        MemberModel member = memberReader.getOrThrow(loginId);
        member.matchesPassword(passwordHasher, loginPw);
        return member;
    }

    @Transactional
    public void changePassword(/* ... */) {
        // 쓰기 작업 (Dirty Checking 활용)
        MemberModel member = memberReader.getOrThrow(loginId);
        member.changePassword(/* ... */);
        // save() 없이도 자동 UPDATE
    }
}
```

### 규칙
- **Service 계층에 적용**: Repository 계층에는 적용하지 않음
- **읽기 전용**: `@Transactional(readOnly = true)`
  - 성능 최적화 (FlushMode.MANUAL)
  - 스냅샷 비교 생략
- **쓰기 작업**: `@Transactional` (기본)
  - Dirty Checking 활성화
  - 예외 발생 시 자동 롤백

---

## 성능 최적화

### N+1 문제 해결

#### Fetch Join 사용
```java
@Query("SELECT m FROM MemberModel m JOIN FETCH m.orders WHERE m.memberId = :memberId")
Optional<MemberModel> findByMemberIdWithOrders(@Param("memberId") MemberId memberId);
```

#### Batch Size 설정
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

### 인덱스 설계
```java
@Entity
@Table(
    name = "member",
    indexes = {
        @Index(name = "idx_member_id", columnList = "member_id"),
        @Index(name = "idx_email", columnList = "email")
    }
)
public class MemberModel extends BaseEntity {
    // ...
}
```

---

## TestContainers 설정

### MySQL TestContainer
```java
@TestConfiguration
public class MySqlTestContainersConfig {

    @Bean
    public MySQLContainer<?> mySQLContainer() {
        MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        container.start();
        return container;
    }
}
```

### DatabaseCleanUp
```java
@Component
public class DatabaseCleanUp {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void truncateAllTables() {
        entityManager.flush();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        List<String> tableNames = entityManager.createNativeQuery(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()"
        ).getResultList();

        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
        }

        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
```

### 테스트에서 사용
```java
@SpringBootTest
class MemberServiceIntegrationTest {

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
}
```

---

## @Query 패턴 (확정 결정)

### EntityManager 직접 사용 금지
프로덕션 코드에서 `EntityManager`를 직접 사용하는 것은 금지. 반드시 JpaRepository의 `@Query`로 대체.

```java
// ❌ 금지: EntityManager 직접 사용
@Autowired
private EntityManager entityManager;

public Page<ProductModel> findProducts(...) {
    Query query = entityManager.createNativeQuery("SELECT ...", ProductModel.class);
    // ...
}

// ✅ 올바름: JpaRepository에 @Query 정의
public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    @Query(value = "SELECT * FROM products WHERE ...", nativeQuery = true)
    Page<ProductModel> findActiveProducts(...);
}
```

### 페이징 네이티브 쿼리: countQuery 필수
```java
// ✅ 페이징 native query는 반드시 countQuery 명시
@Query(
    value = "SELECT p.* FROM products p LEFT JOIN likes l ON p.id = l.ref_product_id " +
            "WHERE p.deleted_at IS NULL GROUP BY p.id ORDER BY COUNT(l.id) DESC",
    countQuery = "SELECT COUNT(*) FROM products p WHERE p.deleted_at IS NULL",
    nativeQuery = true
)
Page<ProductModel> findActiveSortByLikesDesc(Pageable pageable);
```

### 조건부 UPDATE: @Modifying + @Query
```java
// ✅ 재고 차감처럼 조건부 UPDATE는 @Modifying 사용
@Modifying
@Query(value = "UPDATE products SET stock_quantity = stock_quantity - :quantity " +
               "WHERE id = :productId AND stock_quantity >= :quantity", nativeQuery = true)
int decreaseStockIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);
```

### nullable 파라미터 조건 필터링: JPQL 활용
```java
// ✅ null 가능한 파라미터는 JPQL의 조건부 표현식으로 처리
@Query("SELECT o FROM OrderModel o WHERE o.refMemberId = :refMemberId " +
       "AND (:startDateTime IS NULL OR o.createdAt >= :startDateTime) " +
       "AND (:endDateTime IS NULL OR o.createdAt <= :endDateTime) " +
       "ORDER BY o.createdAt DESC")
Page<OrderModel> findByRefMemberIdWithDateFilter(
    @Param("refMemberId") RefMemberId refMemberId,
    @Param("startDateTime") LocalDateTime startDateTime,
    @Param("endDateTime") LocalDateTime endDateTime,
    Pageable pageable
);
```

---

## 주의사항

### Entity 설계
- ❌ **양방향 연관관계 지양**: 단방향 연관관계 우선
- ❌ **Cascade 신중 사용**: 의도하지 않은 삭제 방지
- ✅ **불변 객체 선호**: Value Object는 record 사용
- ✅ **protected 기본 생성자**: JPA 요구사항

### Repository 설계
- ❌ **Service에서 JpaRepository 직접 사용 금지**: RepositoryImpl 경유
- ❌ **EntityManager 직접 사용 금지**: `@Query` 어노테이션으로 대체
- ✅ **Domain Repository 인터페이스**: 도메인 용어 사용
- ✅ **쿼리 메서드 활용**: 간단한 조회는 메서드명으로

### 트랜잭션 설계
- ❌ **Controller에 @Transactional 금지**: Service에서 관리
- ❌ **Lazy Loading 범위 주의**: 트랜잭션 밖에서 접근 시 예외
- ✅ **읽기 전용 명시**: `readOnly = true` 적극 활용
- ✅ **Dirty Checking 활용**: 불필요한 save() 제거
