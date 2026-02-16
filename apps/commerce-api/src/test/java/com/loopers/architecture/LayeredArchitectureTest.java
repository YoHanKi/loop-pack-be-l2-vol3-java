package com.loopers.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 레이어드 아키텍처 의존성 규칙 테스트
 *
 * <p>아키텍처 구조:
 * <pre>
 * Interfaces Layer (Controller, Dto)
 *     ↓
 * Application Layer (Facade, Info)
 *     ↓
 * Domain Layer (Model, Reader, Service, Repository)
 *     ↑
 * Infrastructure Layer (RepositoryImpl, JpaRepository, Converter)
 * </pre>
 */
@DisplayName("레이어드 아키텍처 의존성 규칙")
class LayeredArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("레이어 간 의존성 방향이 올바른지 검증")
    void layer_dependencies_are_respected() {
        Architectures.LayeredArchitecture architecture = layeredArchitecture()
                .consideringAllDependencies()

                // 레이어 정의
                .layer("Interfaces").definedBy("com.loopers.interfaces..")
                .layer("Application").definedBy("com.loopers.application..")
                .layer("Domain").definedBy("com.loopers.domain..")
                .layer("Infrastructure").definedBy("com.loopers.infrastructure..")

                // 예외 1: Domain → Infrastructure.Converter 의존 허용 (JPA @Convert 어노테이션 때문)
                .ignoreDependency(
                        DescribedPredicate.describe(
                                "Domain classes using JPA Converters",
                                javaClass -> javaClass.getPackageName().startsWith("com.loopers.domain")
                        ),
                        DescribedPredicate.describe(
                                "JPA Converter classes",
                                javaClass -> javaClass.getPackageName().contains("infrastructure.jpa.converter")
                        )
                )

                // 예외 2: Infrastructure → Domain.Repository 구현 허용 (DIP 패턴)
                .ignoreDependency(
                        DescribedPredicate.describe(
                                "Infrastructure repository implementations",
                                javaClass -> javaClass.getPackageName().startsWith("com.loopers.infrastructure")
                                        && javaClass.getSimpleName().endsWith("RepositoryImpl")
                        ),
                        DescribedPredicate.describe(
                                "Domain repository interfaces",
                                javaClass -> javaClass.getPackageName().startsWith("com.loopers.domain")
                                        && javaClass.isInterface()
                                        && javaClass.getSimpleName().endsWith("Repository")
                        )
                )

                // 예외 3: 데이터 타입(VO, Enum, Entity)은 모든 레이어에서 사용 가능
                //        컴포넌트(Service, Repository, Facade 등) 간 의존성만 검증
                .ignoreDependency(
                        DescribedPredicate.alwaysTrue(),
                        DescribedPredicate.describe(
                                "Data types (VO, Enum, Entity)",
                                javaClass -> javaClass.getPackageName().contains(".vo")
                                        || javaClass.isEnum()
                                        || javaClass.isAnnotatedWith("jakarta.persistence.Entity")
                        )
                )

                // 의존성 규칙 (다이어그램과 동일한 방향)
                // Interfaces → Application → Domain ↔ Infrastructure
                .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Domain");

        ArchRule rule = architecture
                .because("컴포넌트(Service, Repository, Facade) 간 단방향 의존성을 검증합니다. " +
                        "(데이터 타입(VO/Enum/Entity)은 검증 대상에서 제외)");

        rule.check(classes);
    }

    @Test
    @DisplayName("Interfaces 컴포넌트는 Domain, Infrastructure 컴포넌트를 직접 의존하면 안 됨")
    void interfaces_components_should_not_depend_on_domain_or_infrastructure_components() {
        // Controller가 Service나 Repository를 직접 호출하는 것 금지
        // (VO, Enum, Entity 같은 데이터 타입 의존은 허용)
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.loopers.interfaces..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                    .resideInAnyPackage("com.loopers.domain..", "com.loopers.infrastructure..")
                    .andShould().haveSimpleNameEndingWith("Service")
                    .orShould().haveSimpleNameEndingWith("Repository")
                    .orShould().haveSimpleNameEndingWith("RepositoryImpl")
                .because("Controller는 Facade를 통해서만 하위 레이어에 접근해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Application 컴포넌트는 Infrastructure 컴포넌트를 직접 의존하면 안 됨")
    void application_components_should_not_depend_on_infrastructure_components() {
        // Facade가 Repository 구현체를 직접 호출하는 것 금지
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.loopers.application..")
                .and().haveSimpleNameEndingWith("Facade")
                .should().dependOnClassesThat()
                    .resideInAnyPackage("com.loopers.infrastructure..")
                    .andShould().haveSimpleNameEndingWith("RepositoryImpl")
                .because("Facade는 Domain Service를 통해서만 데이터에 접근해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Domain Service는 Repository 인터페이스만 의존해야 함")
    void domain_services_should_only_depend_on_repository_interfaces() {
        // Service가 RepositoryImpl을 직접 의존하지 않고 Repository 인터페이스만 사용
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.loopers.domain..")
                .and().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat()
                    .resideInAnyPackage("com.loopers.infrastructure..")
                    .andShould().haveSimpleNameNotEndingWith("Converter") // Converter는 예외
                .because("Service는 Repository 인터페이스를 통해 데이터에 접근해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("레이어는 역방향으로 접근할 수 없음 (상위 레이어 접근 금지)")
    void layers_should_not_access_upper_layers() {
        // Application이 Interfaces 접근 금지
        ArchRule applicationToInterfaces = noClasses()
                .that().resideInAPackage("com.loopers.application..")
                .should().dependOnClassesThat().resideInAnyPackage("com.loopers.interfaces..")
                .because("하위 레이어가 상위 레이어를 의존하면 순환 의존성이 발생합니다");

        // Domain이 Interfaces, Application 접근 금지
        ArchRule domainToUpper = noClasses()
                .that().resideInAPackage("com.loopers.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.loopers.interfaces..", "com.loopers.application..")
                .because("하위 레이어가 상위 레이어를 의존하면 순환 의존성이 발생합니다");

        // Infrastructure가 Interfaces, Application 접근 금지
        ArchRule infraToUpper = noClasses()
                .that().resideInAPackage("com.loopers.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.loopers.interfaces..", "com.loopers.application..")
                .because("하위 레이어가 상위 레이어를 의존하면 순환 의존성이 발생합니다");

        applicationToInterfaces.check(classes);
        domainToUpper.check(classes);
        infraToUpper.check(classes);
    }
}
