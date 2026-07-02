pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    val localProperties = java.util.Properties().apply {
        val file = file("local.properties")
        if (file.isFile) {
            file.inputStream().use(::load)
        }
    }

    repositories {
        google()
        mavenCentral()
        // karoo-ext is published to GitHub Packages, which requires authentication.
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: localProperties.getProperty("gpr.user")
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = providers.gradleProperty("gpr.key").orNull
                    ?: localProperties.getProperty("gpr.key")
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
        }
    }
}

rootProject.name = "AeroCam"
include("app")
