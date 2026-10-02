/**
 * Wraps a pnpm workspace package into Gradle's lifecycle:
 * `assemble` -> `pnpm run build`, `check` -> `pnpm run check`.
 */
plugins {
    base
}

val pnpmSources =
    fileTree(projectDir) {
        exclude("node_modules/**", "dist/**", "build/**", "test-results/**", "playwright-report/**")
    }

val pnpmBuild =
    tasks.register<Exec>("pnpmBuild") {
        group = "build"
        description = "Runs `pnpm run build` for ${project.name}."
        dependsOn(":pnpmInstall")
        workingDir(projectDir)
        commandLine("pnpm", "run", "build")
        inputs.files(pnpmSources)
        outputs.dir("dist").optional()
    }

val pnpmCheck =
    tasks.register<Exec>("pnpmCheck") {
        group = "verification"
        description = "Runs `pnpm run check` (types, lint, unit tests) for ${project.name}."
        dependsOn(":pnpmInstall")
        workingDir(projectDir)
        commandLine("pnpm", "run", "check")
        inputs.files(pnpmSources)
    }

tasks.named("assemble") { dependsOn(pnpmBuild) }
tasks.named("check") { dependsOn(pnpmCheck) }
