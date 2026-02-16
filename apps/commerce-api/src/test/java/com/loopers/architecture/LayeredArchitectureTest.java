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
}
