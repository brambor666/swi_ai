package cz.vsb.reservation.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Ochrana hexagonu (ADR-001, evidence-and-evolution.md sekce C). Testy do analýzy nepatří. */
@AnalyzeClasses(packages = "cz.vsb.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Doména nezná infrastrukturu ani žádný framework. */
    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructureOrFrameworks =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure..",
                            "org.springframework..",
                            "jakarta..",
                            "org.hibernate..",
                            "tools.jackson..",
                            "com.fasterxml..",
                            "org.slf4j..");

    /** REST controllery volají jen inbound porty, nikdy repozitáře ani jiné adaptéry. */
    @ArchTest
    static final ArchRule webAdaptersUseOnlyInboundPorts =
            noClasses().that().resideInAPackage("..infrastructure.web..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain.port.out..",
                            "..infrastructure.persistence..",
                            "..infrastructure.notification..");

    /** Adaptéry o sobě navzájem nevědí. */
    @ArchTest
    static final ArchRule persistenceDoesNotDependOnOtherAdapters =
            noClasses().that().resideInAPackage("..infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure.web..",
                            "..infrastructure.notification..");

    /** JPA entity jsou čistě technické a doménu nezná (proto je state String, ne enum). */
    @ArchTest
    static final ArchRule jpaEntitiesDoNotDependOnDomain =
            noClasses().that().haveSimpleNameEndingWith("JpaEntity")
                    .should().dependOnClassesThat().resideInAPackage("..domain..");
}