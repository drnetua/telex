plugins {
    id("spring.conventions")
    id("kotlin.conventions")
    kotlin("plugin.spring")
}

// Bean Validation container-element constraints (`List<@Size(max = 200) String>`) need the annotations in bytecode.
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
    }
}
