plugins {
    id("pnpm.conventions")
}

// The built SPA (`pnpm run build` -> dist/), consumed by backend:app as static resources.
val spa = configurations.consumable("spa")

artifacts {
    add(spa.name, layout.projectDirectory.dir("dist")) {
        builtBy("pnpmBuild")
    }
}
