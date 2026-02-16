# Loopers Commerce Platform - 개발 가이드

## 프로젝트 개요

Spring Boot 기반 멀티모듈 커머스 플랫폼

### 핵심 기술 스택
- **Java 21** + **Spring Boot 3.4.4** + **Gradle (Kotlin DSL)**
- **JPA + QueryDSL**, **Redis**, **Kafka**, **MySQL 8.x**
- **SpringDoc OpenAPI 2.7.0** (Swagger UI)
- **TestContainers**, **JUnit 5**, **AssertJ**, **Mockito**
- **Prometheus + Grafana** (모니터링)

---

## 모듈 구조

```
Root
├── apps/                 # 실행 가능한 애플리케이션
│   ├── commerce-api      # REST API 서버
│   ├── commerce-batch    # 배치 작업
│   └── commerce-streamer # Kafka 스트리밍
├── modules/              # 재사용 가능한 인프라
│   ├── jpa              # JPA, QueryDSL 설정
│   ├── redis            # Redis 설정
│   └── kafka            # Kafka 설정
└── supports/             # 부가 기능
    ├── jackson          # JSON 직렬화
    ├── logging          # Logback 설정
    └── monitoring       # Actuator, Prometheus
```

### 의존성 규칙
- apps → modules, supports (의존 가능)
- modules ↔ modules (상호 의존 금지)
- supports ↔ supports (상호 의존 금지)
- modules, supports → apps (의존 불가)

---

## 아키텍처 - 레이어드 아키텍처

```
Interfaces Layer (Controller, ApiSpec, Dto)
    ↓
Application Layer (Facade, Info)
    ↓
Domain Layer (Model, Reader, Service, Repository, VO)
    ↓
Infrastructure Layer (RepositoryImpl, JpaRepository, Converter)
```

### 레이어별 핵심 책임

**Domain Layer** - 핵심 비즈니스 로직
- **Model**: JPA Entity, `BaseEntity` 상속, 정적 팩토리 `create()`, 도메인 행위 메서드
- **Reader**: 읽기 전용 조회, VO 변환, 조회+예외 통합 (`getOrThrow`)
- **Service**: 교차 엔티티 규칙 (중복 체크 등), 트랜잭션 관리
- **Value Object**: `record` 타입, Compact Constructor 검증, 불변

**Application Layer** - 유스케이스 조합
- **Facade**: 여러 도메인 서비스 조합

**Interfaces Layer** - 외부 통신
- **Controller**: REST API, `ApiResponse` 반환
- **Dto**: `record` 타입, Jakarta Validation

**Infrastructure Layer** - 기술 구현
- **RepositoryImpl**: Domain Repository 구현
- **JpaRepository**: Spring Data JPA
- **Converter**: Value Object ↔ DB 변환

---

## 코딩 컨벤션

### 네이밍 규칙
- Entity: `{Domain}Model` (예: `MemberModel`)
- Reader: `{Domain}Reader`
- Service: `{Domain}Service`
- Repository: `{Domain}Repository` / `{Domain}RepositoryImpl` / `{Domain}JpaRepository`
- Controller: `{Domain}V{version}Controller`
- DTO: `{Domain}V{version}Dto`
- Value Object: `{Name}` (예: `MemberId`, `Email`)
- Converter: `{ValueObject}Converter`

### 타입 사용
- **Entity**: `class` (가변 상태)
- **Value Object**: `record` (불변)
- **DTO**: `record` (불변)

### 의존성 주입
- `@RequiredArgsConstructor` + `private final` (생성자 주입)

---

## 개발 워크플로우 - TDD

### Red → Green → Refactor
1. **Red**: 실패하는 테스트 먼저 작성 (3A 패턴)
2. **Green**: 테스트 통과하는 최소 코드 (오버엔지니어링 금지)
3. **Refactor**: 불필요한 코드 제거, 품질 개선 (모든 테스트 통과 필수)

### 테스트 레벨
- **단위 테스트**: Value Object, 도메인 로직 (외부 의존성 없음)
- **통합 테스트**: Service + Repository (`@SpringBootTest`, TestContainers, DatabaseCleanUp)
- **E2E 테스트**: REST API (`RANDOM_PORT`, TestRestTemplate)

---

## 핵심 원칙

### 진행 Workflow - 증강 코딩
- **대원칙**: 방향성 및 주요 의사 결정은 개발자 승인 필수
- AI는 제안만 가능, 임의 판단 금지
- 중간 결과 보고 및 개발자 개입 허용

### Never Do (절대 금지)
- ❌ 실제 동작하지 않는 코드 작성 금지
- ❌ null-safety 위반 금지 (Optional 활용)
- ❌ println 코드 남기지 말 것 (`@Slf4j` 사용)
- ❌ 테스트 임의 삭제/수정 금지 (`@Disabled`, assertion 약화 금지)

### Recommendation (권장사항)
- ✅ 실제 API를 호출해 확인하는 E2E 테스트 작성
- ✅ 재사용 가능한 객체 설계 (Value Object 활용)
- ✅ 성능 최적화 대안 제안 (N+1 해결, 인덱스, 캐싱)
- ✅ 개발 완료 API는 `.http/**.http`에 작성

### Priority (우선순위)
1. **실제 동작하는 해결책만 고려**
2. **null-safety, thread-safety 고려**
3. **테스트 가능한 구조로 설계**
4. **기존 코드 패턴 분석 후 일관성 유지**

---

## 환경 설정

### 프로파일
- **local**: 로컬 개발 / **test**: 테스트 (TestContainers)
- **dev**: 개발 서버 / **qa**: QA 서버 / **prd**: 운영 서버

---

## 도메인 예시 (Member)

### Value Objects
- **MemberId**: 영문+숫자, 1~10자
- **Email**: RFC 5322, 소문자 정규화
- **BirthDate**: yyyy-MM-dd, 과거 날짜, 130년 이내
- **Password**: 8~16자, 영문 대소문자+숫자+특수문자, 생년월일 불포함

### API 엔드포인트
- `POST /api/v1/members/register`: 회원 가입
- `GET /api/v1/members/me`: 내 정보 조회 (X-Loopers-LoginId, X-Loopers-LoginPw 헤더)
- `PATCH /api/v1/members/me/password`: 비밀번호 수정

---

## 추가 리소스

상세한 가이드는 `.claude/skills/` 디렉토리 참조:
- `architecture`: 레이어드 아키텍처 상세
- `coding-standards`: 네이밍, 타입 패턴
- `testing`: 단위/통합/E2E 테스트 가이드
- `development-workflow`: TDD 상세 프로세스
- `pr-creation`: PR 작성 가이드
- `domain-rules`: 도메인 비즈니스 규칙
- `jpa-database`: JPA, BaseEntity, Converter 패턴

---

## 도메인 & 객체 설계 전략

- 도메인 모델링은 데이터 설계가 아니라 **업무 규칙을 객체 책임으로 고정**하는 작업입니다.
- **Entity**: ID로 동일성 판단, 상태 변화와 연속성이 핵심(행위를 내부에 둠).
- **VO**: 값 자체가 핵심, 불변 + 생성 시 유효성 강제(원시타입 규칙 중복 제거).
- **Domain Service**: 특정 엔티티에 두기 부자연스러운 "도메인 규칙"만, 무상태로 둠.
- **Application Service(Usecase)**: 트랜잭션/권한/저장/외부연동 등 "흐름 조립" 담당, 규칙은 도메인에 위임.
- 규칙이 여러 서비스에 반복되면 → **도메인(엔티티/VO/도메인서비스)로 내려갈 신호**입니다.
- 관계 자체가 의미를 가지면(누가/언제/중복/취소/이력) → `Like`처럼 **독립 도메인으로 분리**합니다.
- 동시성 규칙은 if문만 믿지 말고 **DB 제약(유니크)로 최종 방어선**을 둡니다.
- 의존 방향은 **Interfaces → Application → Domain ← Infrastructure**(Repo 인터페이스는 Domain, 구현은 Infra).
- 리뷰 기준: 도메인이 기술(Spring/JPA/HTTP)을 모르고, 컬렉션/상태 변경은 루트가 통제하며, 테스트는 Fake로 가능해야 합니다.

---

## 아키텍처, 패키지 구성 전략

- **레이어 의존성 방향**: `Controller → Facade → Service → Repository` (단방향), Infrastructure는 Domain 인터페이스 구현 (Port-Adapter).
- **Thin Facade 원칙**: Facade는 Service만 호출하고 Reader 직접 호출 금지, 비즈니스 로직은 Service에 위임(조율만 담당).
- **DTO vs Info vs Model 분리**: DTO(HTTP 계층) → Info(Application 결과 VO) → Model(Domain Entity), 각 레이어 독립성 유지.
- **Reader vs Service**: Reader는 읽기 전용 + getOrThrow 패턴, Service는 CUD + 비즈니스 규칙 + @Transactional 경계.
- **Repository Pattern**: Domain에 Repository 인터페이스(Port), Infrastructure에 구현체(Adapter), Domain이 Infrastructure를 모름.
- **Info 변환**: Facade에서 Model → Info 변환, Controller는 Model 노출 금지(Info만 사용), 레이어 격리 유지.
- **컴포넌트 책임**: Controller(HTTP), Facade(유스케이스 조합), Service(비즈니스 로직), Reader(조회), Repository(영속화).

---

이 문서는 프로젝트의 핵심 원칙과 구조를 요약합니다. 상세 내용은 skills를 참조하세요.
