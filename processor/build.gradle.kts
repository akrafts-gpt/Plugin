plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":api"))
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.ksp.api)
}
