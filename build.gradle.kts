import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "org.renova.renovaattribute"
version = "1.0.0-SNAPSHOT"

repositories {
    maven {
        name = "AiYo Studio Repository"
        url = uri("https://repo.mc9y.com/snapshots")
    }
    maven {
        name = "Purpur"
        url = uri("https://repo.purpurmc.org/snapshots")
    }
    maven {
        name = "PaperMC"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "Lumine"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("com.aystudio.core:AyCore:1.4.5-BETA")
    // MythicMobs API 编译基线：5.11.2 developer dist（Maven release）。
    // 该构件的 Placeholder 工厂已返回 types.* 新接口，与 5.13+ 发行版 API 形态一致；
    // 最低运行时要求 MythicMobs 5.11。构件内 LumineUtils 引用仍是旧包名 io.lumine.utils，
    // 而发行版 plugin jar 已 relocate 到 io.lumine.mythic.bukkit.utils，
    // 故下方 shadowJar 统一 relocate 对齐发行版包名。
    compileOnly("io.lumine:Mythic:5.11.2")
    compileOnly("io.lumine:LumineUtils:1.21-SNAPSHOT")

    implementation("org.luaj:luaj-jse:3.0.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.purpurmc.purpur:purpur-api:1.21.10-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
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
