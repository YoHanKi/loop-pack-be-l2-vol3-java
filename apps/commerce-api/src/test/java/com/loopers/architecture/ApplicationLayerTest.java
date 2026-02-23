package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("Application Layer 규칙")
class ApplicationLayerTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("App은 application 패키지에 위치해야 함")
    void apps_must_reside_in_application() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("App")
                .should().resideInAPackage("..application..")
                .because("App은 단일 도메인 유스케이스를 처리하는 Application Layer에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Facade는 application 패키지에 위치해야 함")
    void facades_must_reside_in_application() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Facade")
                .should().resideInAPackage("..application..")
                .because("Facade는 2개 이상의 App을 조합하는 Application Layer에 위치해야 합니다");

        rule.check(classes);
    }
}
