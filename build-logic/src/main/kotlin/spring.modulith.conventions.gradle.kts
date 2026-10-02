plugins {
    id("spring.conventions")
}

dependencyManagement {
    imports {
        mavenBom(libs.coordinates("spring-modulith-bom"))
    }
}
