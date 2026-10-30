plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bukkit/Spigot API newer than the 1.20.1 baseline. Spigot rather than Paper on the classpath
// keeps everything here usable on plain Spigot servers.
dependencies {
    compileOnly(project(":core"))
    compileOnly(libs.spigot.api.latest)
}
