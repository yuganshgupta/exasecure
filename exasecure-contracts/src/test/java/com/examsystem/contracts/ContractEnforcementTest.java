package com.examsystem.contracts;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

@AnalyzeClasses(packages = "com.examsystem.contracts")
public class ContractEnforcementTest {

    @ArchTest
    public static final ArchRule contracts_must_not_leak_secrets =
        fields().that().areDeclaredInClassesThat().resideInAPackage("..contracts..")
            .should().haveNameNotMatching(".*(?i)password.*")
            .andShould().haveNameNotMatching(".*(?i)correct.*")
            .andShould().haveNameNotMatching(".*(?i)hash.*")
            .allowEmptyShould(true);
}
