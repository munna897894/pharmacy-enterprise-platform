package com.jagapathi.pharmacy.gateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.jagapathi.pharmacy", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule business_services_do_not_depend_on_other_service_implementations =
            noClasses()
                    .that().resideInAPackage("com.jagapathi.pharmacy.gateway..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.jagapathi.pharmacy.auth..",
                            "com.jagapathi.pharmacy.customer..",
                            "com.jagapathi.pharmacy.product..",
                            "com.jagapathi.pharmacy.pharmacy..",
                            "com.jagapathi.pharmacy.inventory..",
                            "com.jagapathi.pharmacy.prescription..",
                            "com.jagapathi.pharmacy.order..",
                            "com.jagapathi.pharmacy.payment..",
                            "com.jagapathi.pharmacy.notification..",
                            "com.jagapathi.pharmacy.audit..",
                            "com.jagapathi.pharmacy.externalmock..");
}
