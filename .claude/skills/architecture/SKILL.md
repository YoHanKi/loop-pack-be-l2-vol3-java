---
name: architecture
description: 레이어드 아키텍처 패턴, 의존성 규칙, Domain/Application/Interfaces/Infrastructure 레이어 구조 및 책임. 아키텍처 설계 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# 아키텍처 패턴

## 레이어드 아키텍처

### 전체 구조
```
┌─────────────────────────────────────────┐
│         Interfaces Layer                │  ← 외부 통신
│  (Controller, ApiSpec, Dto, Response)   │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│        Application Layer                │  ← 유스케이스 조합
│         (Facade, Info)                  │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│          Domain Layer                   │  ← 핵심 비즈니스 로직
│  (Model, Reader, Service, Repository, VO) │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Infrastructure Layer               │  ← 외부 시스템 연동
│  (RepositoryImpl, JpaRepository, etc)   │
└─────────────────────────────────────────┘
```

### 의존성 규칙
- **상위 레이어 → 하위 레이어**: 의존 가능
- **하위 레이어 → 상위 레이어**: 의존 불가
- **Domain Layer**: 다른 레이어에 의존하지 않음 (순수 비즈니스 로직)
- **Infrastructure Layer**: Domain Layer의 인터페이스 구현

---

## Domain Layer (도메인 계층)

### 책임
- 핵심 비즈니스 로직
- 도메인 규칙 및 제약사항
- 엔티티 및 Value Object 관리

### 구성 요소

#### 1. Model (Entity)
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,16}$"
    );

    @Getter
    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;

    @Getter
    @Column(nullable = false)
    private String password;

    protected MemberModel() {}

    public MemberModel(String memberId, String password, String email) {
        this.memberId = new MemberId(memberId);
        this.password = password;
        this.email = new Email(email);
    }

    public static MemberModel create(String memberId, String rawPassword, String email,
                                      String birthDate, String name, Gender gender,
                                      PasswordHasher passwordHasher) {
        validateRawPassword(rawPassword);
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);
        validateGender(gender);
        String hashedPassword = passwordHasher.hash(rawPassword);
        return new MemberModel(memberId, hashedPassword, email, birthDate, name, gender);
    }

    public void matchesPassword(PasswordHasher passwordHasher, String rawPassword) {
        if (!passwordHasher.matches(rawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
    }

    public void changePassword(String rawCurrentPassword, String newRawPassword,
                               PasswordHasher passwordHasher) {
        matchesPassword(passwordHasher, rawCurrentPassword);
        if (passwordHasher.matches(newRawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }
        validateRawPassword(newRawPassword);
        validatePasswordNotContainsBirthDate(newRawPassword,
                this.birthDate != null ? this.birthDate.asString() : null);
        this.password = passwordHasher.hash(newRawPassword);
    }
}
```

**특징**:
- JPA Entity
- `BaseEntity` 상속 (id, createdAt, updatedAt, deletedAt)
- Value Object를 필드로 사용
- 정적 팩토리 메서드 `create()`로 생성 시 검증 + 암호화 캡슐화
- `matchesPassword()`로 비밀번호 검증 위임

#### 2. Value Object
```java
public record MemberId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    public MemberId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId가 비어 있습니다");
        }
        value = value.trim();

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "memberId는 영문+숫자, 1~10자로 이루어져야 합니다: " + value);
        }
    }
}
```

**특징**:
- `record` 타입 (불변)
- Compact Constructor에서 유효성 검증
- 비즈니스 규칙 캡슐화
- 도메인 개념 표현

#### 3. Reader (읽기 전용 도메인 컴포넌트)
```java
@Component
@RequiredArgsConstructor
public class MemberReader {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberModel getMemberByMemberId(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MemberModel getOrThrow(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean existsByMemberId(String memberId) {
        return memberRepository.existsByMemberId(new MemberId(memberId));
    }
}
```

**특징**:
- 읽기 전용 조회 로직 캡슐화
- VO 변환(`new MemberId(memberId)`)을 한 곳에서 관리
- 조회 + 예외 처리를 통합 (`getOrThrow`)
- Service와 Repository 사이의 중간 계층

#### 4. Service
```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final MemberReader memberReader;
    private final PasswordHasher passwordHasher;

    @Transactional
    public MemberModel register(String memberId, String rawPassword, String email,
                                 String birthDate, String name, Gender gender) {
        if (memberReader.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
        }

        MemberModel member = MemberModel.create(memberId, rawPassword, email, birthDate, name, gender, passwordHasher);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String loginPw) {
        MemberModel member = memberReader.getOrThrow(loginId);
        member.matchesPassword(passwordHasher, loginPw);
        return member;
    }

    @Transactional
    public void changePassword(String loginId, String loginPw,
                               String currentPassword, String newPassword) {
        MemberModel member = memberReader.getOrThrow(loginId);
        member.matchesPassword(passwordHasher, loginPw);
        member.changePassword(currentPassword, newPassword, passwordHasher);
    }
}
```

**특징**:
- 도메인 간 조율 및 트랜잭션 관리
- MemberReader를 통한 조회 (읽기 전용 분리)
- MemberModel.create()에 생성 검증 위임
- 중복 체크 등 교차 엔티티 규칙만 Service에서 관리
- changePassword()는 JPA dirty checking으로 자동 반영

#### 5. Repository Interface
```java
public interface MemberRepository {
    MemberModel save(MemberModel member);
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
```

**특징**:
- 도메인 계층에 위치
- 구현체는 Infrastructure 계층
- 도메인 용어 사용
- 기술 세부사항 숨김

---

## Application Layer (응용 계층)

### 책임
- 여러 도메인 서비스 조합
- 유스케이스 구현
- 트랜잭션 경계 설정 (선택적)

### Facade 예시
```java
@Component
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberService memberService;
    private final PointService pointService;
    private final NotificationService notificationService;

    @Transactional
    public MemberInfo registerMemberWithWelcomePoint(MemberInfo.RegisterRequest request) {
        // 1. 회원 가입
        MemberModel member = memberService.register(
            request.memberId(), request.password(), request.email(),
            request.birthDate(), request.name(), request.gender()
        );

        // 2. 웰컴 포인트 지급
        pointService.grantWelcomePoint(member.getMemberId());

        // 3. 가입 환영 알림 발송
        notificationService.sendWelcomeNotification(member.getEmail());

        return MemberInfo.from(member);
    }
}
```

---

## Interfaces Layer (인터페이스 계층)

### 책임
- 외부 요청 수신 및 응답
- 입력 검증 (형식, 필수값)
- DTO ↔ Domain 변환
- HTTP 상태 코드 관리

### Controller 예시
```java
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberService memberService;

    @PostMapping("/register")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> register(
            @Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberModel member = memberService.register(
            request.memberId(), request.password(), request.email(),
            request.birthDate(), request.name(), request.gender()
        );

        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(member);
        return ApiResponse.success(response);
    }
}
```

### ApiResponse 구조
```java
public record ApiResponse<T>(Metadata meta, T data) {
    public record Metadata(Result result, String errorCode, String message) {
        public enum Result { SUCCESS, FAIL }

        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    public static ApiResponse<Object> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(Metadata.fail(errorCode, errorMessage), null);
    }
}
```

---

## Infrastructure Layer (인프라 계층)

### 책임
- 외부 시스템 연동
- 기술적 세부사항 구현
- Domain Repository 구현

### RepositoryImpl
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

---

## 패키지 구조

```
com.loopers
├── domain                          # 도메인 계층
│   ├── member
│   │   ├── MemberModel.java       # Entity
│   │   ├── MemberReader.java      # Reader
│   │   ├── MemberService.java     # Service
│   │   ├── MemberRepository.java  # Repository Interface
│   │   ├── MemberId.java          # Value Object
│   │   ├── Email.java             # Value Object
│   │   └── PasswordHasher.java    # Interface
├── application                     # 응용 계층
│   ├── member
│   │   ├── MemberFacade.java      # Facade
│   │   └── MemberInfo.java        # Info
├── infrastructure                  # 인프라 계층
│   ├── member
│   │   ├── MemberRepositoryImpl.java
│   │   └── MemberJpaRepository.java
│   ├── security
│   │   └── BCryptPasswordHasher.java
│   └── jpa
│       └── converter
│           ├── MemberIdConverter.java
│           └── EmailConverter.java
├── interfaces                      # 인터페이스 계층
│   └── api
│       ├── member
│       │   ├── MemberV1Controller.java
│       │   ├── MemberV1ApiSpec.java
│       │   └── MemberV1Dto.java
│       ├── ApiResponse.java
│       └── ApiControllerAdvice.java
└── support                         # 공통 지원
    └── error
        ├── CoreException.java
        └── ErrorType.java
```

---

## 설계 원칙

### SOLID 원칙 적용
1. **단일 책임 원칙 (SRP)**: 각 레이어/클래스는 하나의 책임만
2. **개방-폐쇄 원칙 (OCP)**: 인터페이스 기반 설계로 확장 용이
3. **리스코프 치환 원칙 (LSP)**: BaseEntity 상속 구조
4. **인터페이스 분리 원칙 (ISP)**: Repository 인터페이스 최소화
5. **의존성 역전 원칙 (DIP)**: Domain → Repository Interface, Infrastructure → 구현

### 모범 사례
- ✅ 레이어 분리 준수
- ✅ 인터페이스 기반 설계
- ✅ Value Object 활용
- ✅ 불변 객체 선호
- ✅ 명확한 네이밍

### 금지 사항
- ❌ 레이어 건너뛰기 (Controller → Repository 직접 호출)
- ❌ 순환 참조
- ❌ 도메인 로직 누수 (Controller에 비즈니스 로직)
- ❌ God Service (하나의 Service에 모든 로직)
