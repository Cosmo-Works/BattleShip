import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.canvasmc.io/public")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "battleship"
for (name in listOf("battleship-api", "battleship-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val battleshipChannel = providers.gradleProperty("channel").get().trim()
    val battleshipBuildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    val versionString = if (battleshipBuildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$battleshipBuildNumber-${battleshipChannel.lowercase()}"
    }
    version = versionString
}
