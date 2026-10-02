import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Type-safe `libs` accessors are not generated for precompiled script plugins,
 * so the catalog is looked up by name instead. `internal` keeps these out of the
 * subprojects' build scripts, where they would shadow the generated `libs` accessors.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias)
        .orElseThrow { IllegalArgumentException("Version '$alias' not found in catalog '$name'") }
        .requiredVersion

/** Returns `group:name:version` of a catalog library, e.g. for `mavenBom(...)`. */
internal fun VersionCatalog.coordinates(alias: String): String {
    val library =
        findLibrary(alias)
            .orElseThrow { IllegalArgumentException("Library '$alias' not found in catalog '$name'") }
            .get()
    return "${library.module}:${library.versionConstraint.requiredVersion}"
}
