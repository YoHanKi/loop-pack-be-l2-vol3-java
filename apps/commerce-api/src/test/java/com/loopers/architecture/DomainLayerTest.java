package com.loopers.architecture;

import com.loopers.domain.BaseEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@DisplayName("Domain Layer 규칙")
class DomainLayerTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .importPackages("com.loopers");
    }

    @Test
    @DisplayName("JPA Entity는 BaseEntity를 상속해야 함")
    void entities_must_extend_base_entity() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Entity.class)
                .should().beAssignableTo(BaseEntity.class)
                .because("모든 Entity는 공통 필드(id, createdAt, updatedAt)를 위해 BaseEntity를 상속해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Value Object는 record 타입이어야 함")
    void value_objects_must_be_records() {
        ArchRule rule = classes()
                .that().resideInAPackage("..vo..")
                .and().areNotNestedClasses()
                .should().beRecords()
                .because("Value Object는 불변성을 보장하기 위해 record 타입을 사용해야 합니다");

        rule.check(classes);
    }

    @Test
    @DisplayName("Service는 domain 패키지에 위치해야 함")
    void service_must_reside_in_application() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Service")
                .should().resideInAPackage("..domain..")
                .because("Service 클래스는 도메인 로직을 포함하므로 domain 패키지에 위치해야 합니다");

        rule.check(classes);
    }
}
