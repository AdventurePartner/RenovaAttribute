import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow)
}

allprojects {
    group = "org.renova.renovaattribute"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        // 1.20.1–1.20.4 服务端运行在 Java 17 上：字节码与可用的 JDK API 都限定为 17。
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xjdk-release=17")
                freeCompilerArgs.add("-Xjsr305=strict")
                freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(17)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":compat-modern"))
    implementation(project(":compat-paper"))
    implementation(project(":compat-mythic"))
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("")
    archiveFileName.set("RenovaAttribute-${project.version}.jar")
    relocate("io.lumine.utils", "io.lumine.mythic.bukkit.utils")
    relocate("kotlin", "org.renova.renovaattribute.lib.kotlin")
    relocate("org.jetbrains", "org.renova.renovaattribute.lib.jetbrains")
    relocate("org.luaj", "org.renova.renovaattribute.lib.luaj")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
