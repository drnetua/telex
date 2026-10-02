// Installs the root pnpm workspace (pnpm-workspace.yaml); every `pnpm.conventions` package depends on it.
// On CI pnpm defaults to --frozen-lockfile.
tasks.register<Exec>("pnpmInstall") {
    group = "build setup"
    description = "Runs `pnpm install` for the pnpm workspace."
    workingDir(layout.projectDirectory)
    commandLine("pnpm", "install")
    inputs.files("package.json", "pnpm-workspace.yaml", "pnpm-lock.yaml", "frontend/package.json", "e2e/package.json")
    outputs.dirs("node_modules", "frontend/node_modules", "e2e/node_modules")
}
