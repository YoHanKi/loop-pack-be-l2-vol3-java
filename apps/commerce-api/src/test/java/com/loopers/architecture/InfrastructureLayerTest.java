package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.AttributeConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("Infrastructure Layer 규칙")
class InfrastructureLayerTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("Repository 구현체는 infrastructure 패키지에 위치해야 함")
    void repository_implementations_must_reside_in_infrastructure() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("RepositoryImpl")
                .should().resideInAPackage("..infrastructure..")
                .because("Repository 구현체는 기술적 세부사항으로 infrastructure 패키지에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("JpaRepository는 infrastructure.jpa 패키지에 위치해야 함")
    void jpa_repositories_must_reside_in_infrastructure_jpa() {
        ArchRule rule = classes()
                .that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage("..infrastructure..")
                .because("Spring Data JPA Repository는 infrastructure.jpa 패키지에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Converter는 'Converter'로 끝나야 함")
    void converters_must_end_with_converter() {
        ArchRule rule = classes()
                .that().implement(AttributeConverter.class)
                .should().haveSimpleNameEndingWith("Converter")
                .because("JPA Converter는 'Converter' 접미사를 사용해야 합니다");

        rule.check(classes);
    }
}
