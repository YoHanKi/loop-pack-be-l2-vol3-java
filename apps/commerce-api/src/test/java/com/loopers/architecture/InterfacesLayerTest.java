package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("Interfaces Layer 규칙")
class InterfacesLayerTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("Controller는 interfaces.api 패키지에 위치해야 함")
    void controllers_must_reside_in_interfaces_api() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..interfaces.api..")
                .because("Controller는 외부 통신을 담당하는 Interfaces Layer에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Controller는 @RestController 어노테이션을 가져야 함")
    void controllers_must_be_annotated_with_rest_controller() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .and().resideInAPackage("..interfaces.api..")
                .should().beAnnotatedWith(RestController.class)
                .because("REST API Controller는 @RestController 어노테이션을 사용해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Dto는 interfaces.api 패키지에 위치해야 함")
    void dtos_must_reside_in_interfaces_api() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Dto")
                .should().resideInAPackage("..interfaces.api..")
                .because("Dto는 API 계층의 데이터 전달 객체로 interfaces.api 패키지에 위치해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Interfaces Layer는 다른 레이어에서 접근하지 않아야 함")
    void interfaces_should_not_be_accessed_by_any_layer() {
        ArchRule rule = classes()
                .that().resideInAPackage("..interfaces..")
                .should().onlyBeAccessed().byClassesThat().resideInAnyPackage("..interfaces..", "");

        // 참고: 빈 패키지("")는 테스트 클래스 등을 허용하기 위함

        rule.check(classes);
    }
}
