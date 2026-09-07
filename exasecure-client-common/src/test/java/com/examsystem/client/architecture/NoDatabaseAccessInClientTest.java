package com.examsystem.client.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.examsystem.client")
public class NoDatabaseAccessInClientTest {

    @ArchTest
    public static final ArchRule client_must_not_access_database_packages =
            noClasses()
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.sql..",
                            "javax.sql..",
                            "com.mysql..",
                            "org.h2..",
                            "com.zaxxer.hikari..",
                            "com.examsystem.server..",
                            "com.examsystem.launcher.."
                    )
                    .allowEmptyShould(true)
                    .because("Client modules must be presentation adapters with NO direct database access");
}
