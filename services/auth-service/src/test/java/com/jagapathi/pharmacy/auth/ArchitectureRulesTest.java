package com.jagapathi.pharmacy.auth;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRulesTest {

    @Test
    void verifyRepositoriesEndWithRepository() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.jagapathi.pharmacy.auth");

        classes()
                .that().haveNameMatching(".*Repository")
                .should().resideInAPackage("com.jagapathi.pharmacy.auth.infrastructure")
                .check(importedClasses);
    }

    @Test
    void verifyControllersEndWithController() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.jagapathi.pharmacy.auth");

        classes()
                .that().haveNameMatching(".*Controller")
                .should().resideInAPackage("com.jagapathi.pharmacy.auth.api..")
                .check(importedClasses);
    }

    @Test
    void verifyServicesEndWithService() {
        JavaClasses importedClasses = new ClassFileImporter()
                .importPackages("com.jagapathi.pharmacy.auth");

        classes()
                .that().haveNameMatching(".*Service")
                .should().resideInAPackage("com.jagapathi.pharmacy.auth.application..")
                .check(importedClasses);
    }
}
