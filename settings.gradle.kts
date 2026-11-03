pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            name = "AiYo Studio Repository"
            url = uri("https://repo.mc9y.com/snapshots")
        }
        maven {
            name = "SpigotMC"
            url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        }
        maven {
            name = "PaperMC"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "Lumine"
            url = uri("https://mvn.lumine.io/repository/maven-public/")
        }
        maven {
            name = "PlaceholderAPI"
            url = uri("https://repo.extendedclip.com/releases/")
        }
        mavenCentral()
    }
}

rootProject.name = "RenovaAttribute"

include("core", "compat-modern", "compat-paper", "compat-mythic")
