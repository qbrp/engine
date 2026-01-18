import org.gradle.kotlin.dsl.testImplementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    id("fabric-loom") version "1.13-SNAPSHOT"
    id("maven-publish")
    kotlin("plugin.serialization") version "2.1.0"
    id("com.gradleup.shadow") version "9.3.0"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("engine") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven") {
                name = "Modrinth"
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
}

val transitive by configurations.creating

dependencies {
    fun shaded(dep: String) {
        implementation(dep)
        transitive(dep)
    }

    val minecraft_version = project.property("minecraft")

    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("fabric_loader")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader")}")
    modImplementation("maven.modrinth:yaml-config:${project.property("yaml_config")}-fabric")
    modImplementation("maven.modrinth:ui-lib:${project.property("ui_lib")}-fabric")
    modApi("maven.modrinth:architectury-api:${project.property("architectury_api")}+fabric")

    include(implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")!!)
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api")}")

    // Kyori Adventure
    val adventure = project.property("adventure_lib_version")
    val adventurePlatform = project.property("adventure_platform_version")
    include(implementation("net.kyori:adventure-text-minimessage:$adventure")!!)
    include(implementation("net.kyori:adventure-text-serializer-gson:$adventure")!!)
    include(implementation("net.kyori:adventure-api:$adventure")!!)
    modImplementation(include("net.kyori:adventure-platform-fabric:$adventurePlatform")!!)

    // Kaml
    shaded("com.charleskorn.kaml:kaml:0.104.0")
    shaded("de.javagl:obj:0.4.0")

    // Permission API
    modImplementation("me.lucko:fabric-permissions-api:${project.property("fabric_permissions_version")}")

    // Game tests
    testImplementation("net.fabricmc:fabric-loader-junit:${project.property("fabric_loader")}")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft"))
    inputs.property("loader_version", project.property("fabric_loader"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft"),
            "loader_version" to project.property("fabric_loader"),
            "kotlin_loader_version" to project.property("kotlin_loader")
        )
    }
}

tasks.shadowJar {
    exclude("kotlin/**")
    exclude("META-INF/kotlin*")
    exclude("*kotlin")

    from(sourceSets.main.get().output)
    from(sourceSets["client"].output)

    configurations = listOf(transitive)
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}