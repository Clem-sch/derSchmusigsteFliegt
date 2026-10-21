plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Abhaengigkeitsregeln aus Abschnitt 4 des Auftrags. Sie werden hier im Build erzwungen,
 * nicht nur dokumentiert: `:app:preBuild` haengt von [verifyModuleDependencies] ab, jeder
 * Build prueft also die Architektur mit.
 *
 * Fuer jedes Modul steht hier die vollstaendige Menge der erlaubten Projekt-Abhaengigkeiten.
 * `:app` darf alles kennen, sonst gilt: Feature- und Game-Module haengen niemals
 * voneinander ab.
 */
val allowedModuleDependencies: Map<String, Set<String>> = mapOf(
    ":core:model" to emptySet(),
    ":core:designsystem" to emptySet(),
    ":core:database" to setOf(":core:model"),
    ":core:sync" to setOf(":core:model"),
    ":core:data" to setOf(":core:model", ":core:database", ":core:sync"),
    ":game:api" to setOf(":core:model", ":core:data"),
    ":game:swipe" to setOf(":core:model", ":core:data", ":core:designsystem", ":game:api"),
    ":feature:catalog" to setOf(":core:model", ":core:data", ":core:designsystem"),
    ":feature:editor" to setOf(":core:model", ":core:data", ":core:designsystem"),
    ":feature:settings" to setOf(":core:model", ":core:data", ":core:designsystem"),
    ":feature:transfer" to setOf(":core:model", ":core:data", ":core:sync", ":core:designsystem"),
)

val verifyModuleDependencies by tasks.registering {
    group = "verification"
    description = "Prueft die Modul-Abhaengigkeitsregeln aus Abschnitt 4 des Auftrags."

    doLast {
        val violations = mutableListOf<String>()

        subprojects.forEach { module ->
            // Container-Projekte (:core, :feature, :game) haben keine eigene Build-Datei.
            if (!module.buildFile.exists()) return@forEach
            val path = module.path
            if (path == ":app") return@forEach

            val allowed = allowedModuleDependencies[path]
            if (allowed == null) {
                violations += "Modul $path ist in den Abhaengigkeitsregeln nicht erfasst."
                return@forEach
            }

            val declared = module.configurations
                .flatMap { configuration -> configuration.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { dependency -> dependency.dependencyProject.path }
                // Das Android-Plugin legt in einigen Konfigurationen eine Abhaengigkeit
                // des Moduls auf sich selbst an; die ist keine Architekturaussage.
                .filter { dependencyPath -> dependencyPath != path }
                .toSortedSet()

            (declared - allowed).forEach { forbidden ->
                violations += "$path darf nicht von $forbidden abhaengen."
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "Verletzung der Modul-Abhaengigkeitsregeln:\n  - ",
                    separator = "\n  - ",
                ),
            )
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        tasks.named("preBuild").configure { dependsOn(verifyModuleDependencies) }
    }
}
