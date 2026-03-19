package com.stablebridge.indexer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.stablebridge.indexer",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String DOMAIN_MODEL = "..domain.model..";
    private static final String DOMAIN_PORT = "..domain.port..";
    private static final String DOMAIN_EVENT = "..domain.event..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String APPLICATION = "..application..";
    private static final String APPLICATION_CONTROLLER = "..application.controller..";

    // -----------------------------------------------------------------------
    // Rule 1: Domain independence — domain must NOT depend on infrastructure
    //         or application packages
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
                    .because("Domain layer must be independent of infrastructure (hexagonal architecture)");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_application =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage(APPLICATION)
                    .because("Domain layer must be independent of application layer (hexagonal architecture)");

    // -----------------------------------------------------------------------
    // Rule 2: Infrastructure implements ports — infrastructure may depend on
    //         domain.port and domain.model but NOT on application.
    //         allowEmptyShould: infrastructure package is not yet populated
    //         in early scaffold phases.
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_application =
            noClasses()
                    .that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAPackage(APPLICATION)
                    .allowEmptyShould(true)
                    .because("Infrastructure adapters must not depend on application layer — "
                            + "they implement domain ports only");

    // -----------------------------------------------------------------------
    // Rule 3: Domain must not import Spring (except @Service, @Transactional,
    //         and beans.factory.annotation)
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_must_not_import_spring_except_allowed =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.data..",
                            "org.springframework.kafka..",
                            "org.springframework.boot..",
                            "org.springframework.context..",
                            "org.springframework.http.."
                    )
                    .because("Domain must not import Spring framework classes except "
                            + "@Service, @Transactional, and beans.factory.annotation");

    // -----------------------------------------------------------------------
    // Rule 4: Domain must not import JPA
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_must_not_import_jpa =
            noClasses()
                    .that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                    .because("Domain model must be persistence-ignorant — "
                            + "JPA annotations belong in infrastructure.persistence");

    // -----------------------------------------------------------------------
    // Rule 5: Infrastructure must not depend on application.controller.
    //         allowEmptyShould: infrastructure package is not yet populated
    //         in early scaffold phases.
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_controllers =
            noClasses()
                    .that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAPackage(APPLICATION_CONTROLLER)
                    .allowEmptyShould(true)
                    .because("Infrastructure must never depend on controllers — "
                            + "controllers are application-layer concerns");

    // -----------------------------------------------------------------------
    // Rule 6: No 'web' package — controllers must live in
    //         application.controller, never in a 'web' package
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule no_web_package_exists =
            noClasses()
                    .should().resideInAPackage("..web..")
                    .because("Controllers must live in application.controller — "
                            + "NEVER create a web package (per coding standards)");

    // -----------------------------------------------------------------------
    // Rule 7: Domain model classes should be records.
    //         Lombok @Builder generates inner *Builder classes that are not
    //         records — exclude them via haveSimpleNameEndingWith("Builder").
    // -----------------------------------------------------------------------

    @ArchTest
    static void domain_model_classes_should_be_records(JavaClasses importedClasses) {
        classes()
                .that().resideInAPackage(DOMAIN_MODEL)
                .and().areNotEnums()
                .and().areNotInterfaces()
                .and().areNotMemberClasses()
                .should().beRecords()
                .because("Domain model classes must be Java records for immutability")
                .check(importedClasses);
    }

    // -----------------------------------------------------------------------
    // Rule 8: Domain event classes should be records.
    //         Lombok @Builder generates inner *Builder classes — exclude them.
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_event_classes_should_be_records =
            classes()
                    .that().resideInAPackage(DOMAIN_EVENT)
                    .and().areNotEnums()
                    .and().areNotInterfaces()
                    .and().areNotMemberClasses()
                    .should().beRecords()
                    .because("Domain event classes must be Java records for immutability");

    // -----------------------------------------------------------------------
    // Rule 9: Domain ports must be interfaces.
    //         allowEmptyShould: port package is not yet populated in early
    //         scaffold phases.
    // -----------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_ports_must_be_interfaces =
            classes()
                    .that().resideInAPackage(DOMAIN_PORT)
                    .should().beInterfaces()
                    .allowEmptyShould(true)
                    .because("Domain ports define contracts and must be interfaces "
                            + "that infrastructure adapters implement");
}
