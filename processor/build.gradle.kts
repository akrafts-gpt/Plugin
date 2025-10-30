plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":api"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.ksp.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.ksp.api)
    testImplementation(libs.ksp.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)
}
