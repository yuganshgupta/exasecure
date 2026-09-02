package com.examsystem.launcher;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.examsystem.launcher")
public class ExaSecureLauncherTest {

    @ArchTest
    public static final ArchRule launcher_must_not_access_database =
            noClasses()
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.sql..",
                            "javax.sql..",
                            "com.mysql..",
                            "org.h2..",
                            "com.zaxxer.hikari.."
                    )
                    .allowEmptyShould(true)
                    .because("Launcher must not contain any database access or business logic");

    @Test
    void findRepoRoot_locatesExaSecureRootWithMavenWrapper() {
        Path root = ExaSecureLauncher.App.findRepoRoot();
        assertThat(root).isNotNull();
        assertThat(Files.exists(root.resolve("pom.xml"))).isTrue();
        assertThat(Files.exists(root.resolve("mvnw")) || Files.exists(root.resolve("mvnw.cmd"))).isTrue();
    }
}

