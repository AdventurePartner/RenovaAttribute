plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // core 只能看到最低支持版本的 API：误用新版或 Paper 专有 API 会直接编译失败，
    // 这类实现放进 compat-modern / compat-paper，由 platform.Platform 在运行时按能力选择。
    compileOnly(libs.spigot.api.min)
    compileOnly(libs.aycore)
    compileOnly(libs.mythic)
    compileOnly(libs.lumine.utils)
    compileOnly(libs.placeholderapi)

    implementation(libs.luaj)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spigot.api.min)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
