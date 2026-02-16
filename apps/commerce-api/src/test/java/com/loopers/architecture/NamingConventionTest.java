package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("네이밍 규칙")
class NamingConventionTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("JPA Entity는 'Model'로 끝나야 함")
    void entities_must_end_with_model() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Entity.class)
                .should().haveSimpleNameEndingWith("Model")
                .because("JPA Entity는 'Model' 접미사를 사용하여 도메인 모델임을 명확히 해야 합니다");

        rule.check(classes);
    }
}
