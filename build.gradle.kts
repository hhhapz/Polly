import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "me.moeszyslak"
version = Versions.BOT

plugins {
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("me.jakejmattson:DiscordKt:${Versions.DISCORDKT}")
    implementation("me.xdrop:fuzzywuzzy:${Versions.FUZZY}")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    shadowJar {
        archiveFileName.set("Polly.jar")
        manifest {
            attributes(
                    "Main-Class" to "me.moeszyslak.polly.MainAppKt"
            )
        }
    }
}

object Versions {
    const val BOT = "1.0.0"
    const val DISCORDKT = "0.21.3"
    const val FUZZY = "1.3.1"
}