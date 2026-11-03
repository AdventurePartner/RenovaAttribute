plugins {
    alias(libs.plugins.kotlin.jvm)
}

// MythicMobs API newer than the 5.6.2 baseline. Loaded only after the Mythic hook's probe
// finds the matching classes, so nothing here links on older MythicMobs versions.
dependencies {
    compileOnly(project(":core"))
    compileOnly(libs.mythic.dist)
    compileOnly(libs.spigot.api.min)
}
