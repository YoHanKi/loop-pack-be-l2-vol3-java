---
name: development-workflow
description: TDD 워크플로우 (Red→Green→Refactor), 증강 코딩 원칙, 개발자 승인 프로세스. 개발 작업 시 필수 참조
user-invocable: true
allowed-tools: Read, Grep, Bash, Edit, Write
---

# 개발 워크플로우

## 진행 Workflow - 증강 코딩

### 대원칙
**방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행**

### 핵심 원칙

#### 1. 중간 결과 보고
- AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현할 경우 개발자가 개입
- 각 단계별 진행 상황을 명확히 보고
- 예상치 못한 문제 발생 시 즉시 보고

#### 2. 설계 주도권 유지
- AI가 임의 판단을 하지 않음
- 방향성에 대한 제안은 가능하나 개발자의 승인 필수
- 아키텍처 변경, 새로운 패턴 도입 시 반드시 사전 협의

#### 3. 명확한 커뮤니케이션
- 작업 범위를 명확히 정의
- 불확실한 부분은 가정하지 않고 질문
- 완료 기준을 명확히 설정

---

## 개발 Workflow - TDD (Red > Green > Refactor)

### 전체 흐름
```
Red Phase (실패하는 테스트)
    ↓
Green Phase (테스트 통과 코드)
    ↓
Refactor Phase (코드 개선)
    ↓
반복
```

---

### 1. Red Phase: 실패하는 테스트 먼저 작성

#### 목표
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트가 실패하는 것을 확인

#### 작업 순서
1. **요구사항 분석**: 구현할 기능의 명세 파악
2. **테스트 케이스 작성**: 3A 패턴으로 테스트 작성
3. **테스트 실행**: 실패 확인 (Red)

#### 예시: 회원 가입 기능
```java
@DisplayName("회원 가입 시,")
@Nested
class Register {
    @DisplayName("유저 저장이 정상적으로 이루어진다.")
    @Test
    void testUserSave() {
        // arrange
        String memberId = "testuser1";
        String password = "Test1234!";
        String email = "test@example.com";
        String birthDate = "1995-05-20";
        String name = "테스트유저";
        Gender gender = Gender.MALE;

        // act
        MemberModel savedMember = memberService.register(
            memberId, password, email, birthDate, name, gender
        );

        // assert
        assertAll(
            () -> assertThat(savedMember).isNotNull(),
            () -> assertThat(savedMember.getId()).isNotNull(),
            () -> assertThat(savedMember.getMemberId().value()).isEqualTo(memberId)
        );
    }
}
```

#### 체크리스트
- [ ] 테스트 케이스가 요구사항을 정확히 반영하는가?
- [ ] 3A 패턴을 준수하는가?
- [ ] @DisplayName이 명확한가?
- [ ] 테스트가 실패하는가? (Red 확인)

---

### 2. Green Phase: 테스트를 통과하는 코드 작성

#### 목표
- Red Phase의 테스트가 모두 통과할 수 있는 코드 작성
- **오버엔지니어링 금지**: 최소한의 코드로 테스트 통과

#### 작업 순서
1. **최소 구현**: 테스트를 통과시키는 최소한의 코드
2. **테스트 실행**: 통과 확인 (Green)
3. **추가 테스트**: 엣지 케이스 테스트 추가 및 구현

#### 예시: MemberService 구현
```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public MemberModel register(String memberId, String rawPassword, String email,
                                 String birthDate, String name, Gender gender) {
        // 최소 구현: 테스트 통과를 위한 코드
        String hashedPassword = passwordHasher.hash(rawPassword);
        MemberModel member = new MemberModel(memberId, hashedPassword, email, birthDate, name, gender);
        return memberRepository.save(member);
    }
}
```

#### 체크리스트
- [ ] 모든 테스트가 통과하는가? (Green 확인)
- [ ] 불필요한 코드가 없는가?
- [ ] 오버엔지니어링을 하지 않았는가?

---

### 3. Refactor Phase: 불필요한 코드 제거 및 품질 개선

#### 목표
- 코드 품질 개선
- 중복 제거
- 가독성 향상
- **모든 테스트 케이스가 통과해야 함**

#### 작업 순서
1. **코드 리뷰**: 개선 가능한 부분 파악
2. **리팩토링**: 코드 개선
3. **테스트 실행**: 모든 테스트 통과 확인
4. **반복**: 더 이상 개선할 부분이 없을 때까지

#### 리팩토링 대상
1. **불필요한 private 함수 제거**
   - 한 곳에서만 사용되는 private 메서드
   - 과도하게 분리된 메서드

2. **객체지향적 코드 작성**
   - 책임 분리
   - 캡슐화 강화
   - Value Object 활용

3. **Unused Import 제거**
   - 사용하지 않는 import 문 삭제

4. **성능 최적화**
   - N+1 쿼리 문제 해결
   - 불필요한 DB 조회 제거
   - 캐싱 적용 검토

#### 예시: 리팩토링 전후
```java
// Before: 과도한 private 메서드 분리
@Service
public class MemberService {
    public MemberModel register(/* ... */) {
        validateMemberId(memberId);
        validatePassword(password);
        validateEmail(email);
        // ...
    }

    private void validateMemberId(String memberId) {
        if (memberId == null) throw new CoreException(/* ... */);
    }

    private void validatePassword(String password) {
        if (password == null) throw new CoreException(/* ... */);
    }

    private void validateEmail(String email) {
        if (email == null) throw new CoreException(/* ... */);
    }
}

// After: Value Object로 책임 이동
@Service
public class MemberService {
    public MemberModel register(String memberId, String password, String email, /* ... */) {
        // Value Object 생성 시 자동 검증
        MemberModel member = MemberModel.create(memberId, password, email, /* ... */);
        return memberRepository.save(member);
    }
}

// Value Object에서 검증
public record MemberId(String value) {
    public MemberId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId가 비어 있습니다");
        }
        // ...
    }
}
```

#### 체크리스트
- [ ] 불필요한 private 함수를 제거했는가?
- [ ] 객체지향 원칙을 준수하는가?
- [ ] Unused import를 제거했는가?
- [ ] 성능 최적화를 고려했는가?
- [ ] 모든 테스트가 통과하는가?

---

## 주의사항

### 1. Never Do (절대 금지)

#### 실제 동작하지 않는 코드 작성 금지
```java
// ❌ 잘못된 예: Mock 데이터로만 동작
@Test
void testWithFakeData() {
    MemberModel fakeMember = new MemberModel();
    // 실제 저장 없이 테스트만 통과
}

// ✅ 올바른 예: 실제 동작 검증
@Test
void testWithRealData() {
    MemberModel member = memberService.register(/* ... */);
    MemberModel found = memberRepository.findById(member.getId()).orElseThrow();
    assertThat(found.getMemberId()).isEqualTo(member.getMemberId());
}
```

#### null-safety 위반 금지
```java
// ❌ 잘못된 예: null 반환
public MemberModel findMember(String memberId) {
    return memberRepository.findByMemberId(memberId).orElse(null);
}

// ✅ 올바른 예: Optional 사용
public Optional<MemberModel> findMember(String memberId) {
    return memberRepository.findByMemberId(memberId);
}

// ✅ 또는 예외 발생
public MemberModel findMember(String memberId) {
    return memberRepository.findByMemberId(memberId)
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
}
```

#### println 코드 남기지 말 것
```java
// ❌ 잘못된 예
public void register(/* ... */) {
    System.out.println("회원 가입 시작");
    // ...
}

// ✅ 올바른 예
@Slf4j
public class MemberService {
    public void register(/* ... */) {
        log.info("회원 가입 시작: memberId={}", memberId);
        // ...
    }
}
```

#### 테스트 임의 삭제/수정 금지
```java
// ❌ 절대 금지
@Test
@Disabled("나중에 수정")  // 테스트 비활성화
void testSomething() {
    // ...
}

// ❌ 절대 금지: 테스트를 통과시키기 위해 assertion 약화
@Test
void testSomething() {
    // assertThat(result).isEqualTo(expected);  // 주석 처리
    assertThat(result).isNotNull();  // 약한 검증으로 변경
}
```

---

### 2. Recommendation (권장사항)

#### E2E 테스트 작성
- 실제 HTTP 요청/응답 검증
- TestRestTemplate 사용
- 전체 시나리오 테스트

#### 재사용 가능한 객체 설계
- Value Object 활용
- 불변 객체 우선
- 정적 팩토리 메서드 제공

#### 성능 최적화 제안
- N+1 문제 해결 (Fetch Join, Batch Size)
- 인덱스 설계
- 캐싱 전략 (Redis)

#### HTTP Client 파일 작성
```http
### 회원 가입
POST http://localhost:8080/api/v1/members/register
Content-Type: application/json

{
  "memberId": "testuser1",
  "password": "Test1234!",
  "email": "test@example.com",
  "birthDate": "1995-05-20",
  "name": "테스트유저",
  "gender": "MALE"
}
```

---

### 3. Priority (우선순위)

#### 1순위: 실제 동작하는 해결책만 고려
- Mock 데이터가 아닌 실제 데이터로 검증
- 테스트가 실제 시나리오를 반영

#### 2순위: null-safety, thread-safety 고려
- Optional 사용
- 불변 객체 선호 (record, final)
- 동시성 문제 고려

#### 3순위: 테스트 가능한 구조로 설계
- 의존성 주입
- 인터페이스 기반 설계
- 순수 함수 선호

#### 4순위: 기존 코드 패턴 분석 후 일관성 유지
- 프로젝트의 기존 패턴 준수
- 네이밍 컨벤션 일관성
- 아키텍처 패턴 준수

---

## 코드 리뷰 체크리스트

### 기능 구현
- [ ] 요구사항을 모두 만족하는가?
- [ ] 엣지 케이스를 처리하는가?
- [ ] 에러 처리가 적절한가?

### 테스트
- [ ] 단위 테스트가 작성되었는가?
- [ ] 통합 테스트가 작성되었는가?
- [ ] E2E 테스트가 작성되었는가?
- [ ] 모든 테스트가 통과하는가?

### 코드 품질
- [ ] 코드가 읽기 쉬운가?
- [ ] 중복 코드가 없는가?
- [ ] 네이밍이 명확한가?
- [ ] Unused import가 없는가?

### 아키텍처
- [ ] 레이어 분리가 적절한가?
- [ ] 의존성 방향이 올바른가?
- [ ] SOLID 원칙을 준수하는가?

### 성능
- [ ] N+1 쿼리 문제가 없는가?
- [ ] 불필요한 DB 조회가 없는가?
- [ ] 적절한 인덱스가 있는가?

### 보안
- [ ] 입력 검증이 적절한가?
- [ ] SQL Injection 위험이 없는가?
- [ ] 민감 정보가 로그에 남지 않는가?
