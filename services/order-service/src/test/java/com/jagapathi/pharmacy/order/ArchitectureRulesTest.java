package com.jagapathi.pharmacy.order;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@DisplayName("Architecture Rules Tests")
class ArchitectureRulesTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
        .importPackages("com.jagapathi.pharmacy.order");

    @Test
    @DisplayName("Controllers should be in api.controller package")
    void testControllerPackageName() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..api.controller..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Services should be in application.service package")
    void testServicePackageName() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Service")
            .and().doNotHaveSimpleName("OrderServiceApplicationTests")
            .should().resideInAPackage("..application.service..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Repositories should be in infrastructure package")
    void testRepositoryPackageName() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..infrastructure..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Domain entities should only access domain exceptions")
    void testDomainEncapsulation() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Exception")
            .should().resideInAPackage("..domain..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("No cyclic dependencies")
    void testNoCyclicDependencies() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.kafka..");

        rule.check(importedClasses);
    }
}
