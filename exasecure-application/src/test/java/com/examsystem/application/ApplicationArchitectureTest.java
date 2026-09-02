package com.examsystem.application;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.examsystem.application")
public class ApplicationArchitectureTest {

    @ArchTest
    public static final ArchRule application_should_not_depend_on_frameworks =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "java.sql..",
                "javax.swing..",
                "java.awt..",
                "javafx..",
                "com.mysql..",
                "java.net.http..",
                "javax.servlet.."
            ).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule application_should_not_depend_on_network_contracts =
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.examsystem.contracts.."
            ).allowEmptyShould(true);
}
