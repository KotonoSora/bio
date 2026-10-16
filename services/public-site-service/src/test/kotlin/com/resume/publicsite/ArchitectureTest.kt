package com.resume.publicsite

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Layered packages (api, domain, data, config); extraction stays a move, not a rewrite.
 * api may not access data directly; config may not access domain internals.
 */
class ArchitectureTest {

    private val classes: JavaClasses = ClassFileImporter().importPackages("com.resume.publicsite")

    @Test
    fun `api must not depend on data`() {
        noClasses()
            .that().resideInAPackage("com.resume.publicsite.api..")
            .should().dependOnClassesThat().resideInAPackage("com.resume.publicsite.data..")
            .check(classes)
    }
}
