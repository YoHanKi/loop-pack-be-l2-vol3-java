---
name: domain-rules
description: Member 도메인 비즈니스 규칙 (MemberId, Email, BirthDate, Password 검증 규칙). 도메인 로직 구현 시 참조
user-invocable: true
allowed-tools: Read, Grep
---

# 도메인 비즈니스 규칙

## Member 도메인

### Value Objects 검증 규칙

#### MemberId
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

**규칙**:
- **형식**: 영문 대소문자 + 숫자만 허용
- **길이**: 1~10자
- **정규식**: `^[A-Za-z0-9]{1,10}$`
- **전처리**: trim() 적용
- **null 불가**: null 또는 공백 시 예외

---

#### Email
```java
public record Email(String address) {
    private static final Pattern PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public Email {
        if (address == null || address.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email이 비어 있습니다");
        }
        address = address.trim().toLowerCase();

        if (address.length() > 254) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email은 최대 254자까지 허용됩니다");
        }

        if (!PATTERN.matcher(address).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email 형식이 올바르지 않습니다");
        }
    }
}
```

**규칙**:
- **형식**: RFC 5322 기반 (간소화 버전)
- **길이**: 최대 254자
- **정규화**: 소문자 변환 (`toLowerCase()`)
- **전처리**: trim() 적용
- **null 불가**: null 또는 공백 시 예외

---

#### BirthDate
```java
public record BirthDate(LocalDate date) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public BirthDate(String dateString) {
        this(parseAndValidate(dateString));
    }

    public BirthDate {
        if (date == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "birthDate가 비어 있습니다");
        }

        LocalDate now = LocalDate.now();
        if (date.isAfter(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다");
        }

        LocalDate earliest = now.minusYears(130);
        if (date.isBefore(earliest)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 130년 이전일 수 없습니다");
        }
    }

    private static LocalDate parseAndValidate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "birthDate가 비어 있습니다");
        }

        try {
            return LocalDate.parse(dateString.trim(), FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "birthDate는 yyyy-MM-dd 형식이어야 합니다");
        }
    }

    public String asString() {
        return date.format(FORMATTER);
    }
}
```

**규칙**:
- **형식**: yyyy-MM-dd
- **범위**: 현재 날짜 기준 과거 130년 이내 ~ 현재
- **미래 날짜 불가**: 현재보다 이후 날짜 거부
- **과거 제한**: 130년 이전 날짜 거부
- **변환**: String → LocalDate (생성자), LocalDate → String (`asString()`)
- **null 불가**: null 또는 공백 시 예외

---

#### Name
```java
public record Name(String value) {
    public Name {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "name이 비어 있습니다");
        }
        value = value.trim();

        if (value.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "name은 최대 50자까지 허용됩니다");
        }
    }
}
```

**규칙**:
- **길이**: 1~50자
- **공백 허용**: 이름 내부 공백 가능
- **전처리**: trim() 적용
- **null 불가**: null 또는 공백 시 예외

---

#### Gender
```java
public enum Gender {
    MALE, FEMALE, OTHER
}
```

**규칙**:
- **필수 입력**: null 불가 (`@NotNull` 검증)
- **허용 값**: MALE, FEMALE, OTHER

---

### Password 규칙

#### 비밀번호 검증 (MemberModel 내부)
```java
private static final Pattern PASSWORD_PATTERN = Pattern.compile(
    "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,16}$"
);

private static void validateRawPassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
        throw new CoreException(ErrorType.BAD_REQUEST, "password가 비어 있습니다");
    }

    if (!PASSWORD_PATTERN.matcher(rawPassword).matches()) {
        throw new CoreException(ErrorType.BAD_REQUEST,
            "password는 8~16자, 영문 대소문자+숫자+특수문자를 모두 포함해야 합니다");
    }
}

private static void validatePasswordNotContainsBirthDate(String rawPassword, String birthDate) {
    if (birthDate == null || birthDate.isBlank()) {
        return;
    }

    String yyyy = birthDate.substring(0, 4);
    String mmdd = birthDate.substring(5).replace("-", "");
    String yyyymmdd = birthDate.replace("-", "");

    if (rawPassword.contains(yyyy) || rawPassword.contains(mmdd) || rawPassword.contains(yyyymmdd)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "password는 생년월일을 포함할 수 없습니다");
    }
}
```

**규칙**:
- **길이**: 8~16자
- **구성**:
  - 영문 소문자 최소 1개
  - 영문 대문자 최소 1개
  - 숫자 최소 1개
  - 특수문자 최소 1개 (`!@#$%^&*()_+-=[]{};':\"\\|,.<>/?`)
- **생년월일 제약**:
  - yyyy (4자리 연도) 포함 불가
  - MMdd (월일 4자리) 포함 불가
  - yyyyMMdd (전체 8자리) 포함 불가
- **저장 방식**: BCrypt 해시로 암호화하여 저장 (원문 저장 금지)

---

### 비즈니스 규칙

#### 회원 가입
```java
@Transactional
public MemberModel register(String memberId, String rawPassword, String email,
                             String birthDate, String name, Gender gender) {
    // 1. 중복 체크 (교차 엔티티 규칙)
    if (memberReader.existsByMemberId(memberId)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
    }

    // 2. Model.create()로 생성 (단일 엔티티 규칙 위임)
    MemberModel member = MemberModel.create(memberId, rawPassword, email, birthDate, name, gender, passwordHasher);
    return memberRepository.save(member);
}
```

**규칙**:
- **중복 가입 방지**: memberId 중복 시 `BAD_REQUEST` 예외
- **검증 위임**: Model.create()에서 VO 생성 + 비밀번호 검증 수행
- **암호화**: PasswordHasher로 BCrypt 해시 생성
- **트랜잭션**: 쓰기 작업이므로 `@Transactional` 적용

---

#### 인증 (로그인)
```java
@Transactional(readOnly = true)
public MemberModel authenticate(String loginId, String loginPw) {
    MemberModel member = memberReader.getOrThrow(loginId);
    member.matchesPassword(passwordHasher, loginPw);
    return member;
}
```

**규칙**:
- **회원 조회**: Reader.getOrThrow()로 존재하지 않으면 `NOT_FOUND` 예외
- **비밀번호 검증**: Model.matchesPassword()로 위임, 불일치 시 `BAD_REQUEST` 예외
- **트랜잭션**: 읽기 전용 (`readOnly = true`)

---

#### 비밀번호 변경
```java
@Transactional
public void changePassword(String loginId, String loginPw,
                           String currentPassword, String newPassword) {
    MemberModel member = memberReader.getOrThrow(loginId);
    member.matchesPassword(passwordHasher, loginPw);
    member.changePassword(currentPassword, newPassword, passwordHasher);
}
```

**규칙**:
- **2단계 인증**:
  1. 로그인 인증 (loginPw 검증)
  2. 현재 비밀번호 재확인 (currentPassword 검증)
- **새 비밀번호 제약**:
  - 기존 비밀번호와 동일 불가
  - Password 검증 규칙 통과 필수
  - 생년월일 포함 불가
- **JPA Dirty Checking**: 명시적 save() 없이 트랜잭션 커밋 시 자동 반영
- **트랜잭션**: 쓰기 작업이므로 `@Transactional` 적용

---

## API 헤더 인증 (임시)

### 헤더 규칙
- **X-Loopers-LoginId**: 회원 ID
- **X-Loopers-LoginPw**: 원문 비밀번호 (BCrypt 검증용)

### 적용 API
- `GET /api/v1/members/me`: 내 정보 조회
- `PATCH /api/v1/members/me/password`: 비밀번호 수정

**주의**: 실제 운영 환경에서는 JWT 또는 세션 기반 인증으로 대체 필요

---

## 예외 타입 매핑

| 상황 | ErrorType | HTTP Status |
|------|-----------|-------------|
| VO 검증 실패 (형식, 길이 등) | `BAD_REQUEST` | 400 |
| 중복 가입 | `BAD_REQUEST` | 400 |
| 회원 조회 실패 | `NOT_FOUND` | 404 |
| 비밀번호 불일치 | `BAD_REQUEST` | 400 |
| 새 비밀번호가 기존과 동일 | `BAD_REQUEST` | 400 |
| Validation 실패 (@NotNull 등) | `BAD_REQUEST` | 400 |

---

## 테스트 데이터 예시

### 유효한 데이터
```java
String VALID_MEMBER_ID = "testuser1";
String VALID_PASSWORD = "Test1234!";
String VALID_EMAIL = "test@example.com";
String VALID_BIRTH_DATE = "1995-05-20";
String VALID_NAME = "테스트유저";
Gender VALID_GENDER = Gender.MALE;
```

### 무효한 데이터
```java
// MemberId
"invalid_id!"      // 특수문자 포함
"toolongidname123" // 11자 초과
""                 // 공백

// Email
"invalid_email"    // @ 없음
"test@"            // 도메인 없음
"@example.com"     // 로컬 부분 없음

// BirthDate
"2025-12-31"       // 미래 날짜
"1800-01-01"       // 130년 이전
"1995/05/20"       // 잘못된 형식

// Password
"short1A!"         // 8자 미만
"nouppercase1!"    // 대문자 없음
"NOLOWERCASE1!"    // 소문자 없음
"NoNumber!"        // 숫자 없음
"NoSpecial1Aa"     // 특수문자 없음
"Test19950520!"    // 생년월일 포함 (birthDate: 1995-05-20)
```
