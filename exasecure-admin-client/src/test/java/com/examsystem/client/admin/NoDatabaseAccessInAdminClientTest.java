package com.examsystem.client.admin;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.examsystem.client.admin")
public class NoDatabaseAccessInAdminClientTest {

    @ArchTest
    public static final ArchRule admin_client_must_not_access_database_packages =
            noClasses()
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.sql..",
                            "javax.sql..",
                            "com.mysql..",
                            "org.h2..",
                            "com.zaxxer.hikari.."
                    )
                    .allowEmptyShould(true)
                    .because("Admin client must be a presentation adapter with NO database access");
}
