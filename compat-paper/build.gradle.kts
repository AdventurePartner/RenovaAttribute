plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Paper-only API. Each implementation is loaded only after its own capability probe passes,
// so classes here may use API from any Paper version up to the one compiled against.
dependencies {
    compileOnly(project(":core"))
    compileOnly(libs.paper.api)
}

// paper-api's Gradle metadata requires a Java 21 consumer. The output stays Java 17 bytecode:
// Paper 1.20.1–1.20.4 runs on Java 17 and loads the parts of this module it supports.
configurations.compileClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}
