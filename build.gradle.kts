
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.1.0"
    id("fabric-loom") version "1.16-SNAPSHOT"
    id("com.gradleup.shadow") version "9.3.0"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}
java {
    withSourcesJar()
}

val jdkVersion = 21
kotlin {
    jvmToolchain(jdkVersion)
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("engine") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    accessWidenerPath = file("src/client/resources/engine.classtweaker")
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

    maven("https://maven.logandark.net")

    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/qbrp/cyberia")
        credentials {
            username = findProperty("gpr.user") as String?
            password = findProperty("gpr.key") as String?
        }
    }
    maven {
        name = "Gegy"
        url = uri("https://maven.gegy.dev")
    }
    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven("https://maven.wispforest.io/releases/2412")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}


val transitive by configurations.creating

dependencies {
    fun shaded(dep: String) {
        implementation(dep)
        transitive(dep)
    }

    val minecraft_version = project.property("minecraft")

    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("fabric_loader")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api")}")

    // YAML Config

    modImplementation("maven.modrinth:yaml-config:${project.property("yaml_config")}-fabric")
    modImplementation("maven.modrinth:ui-lib:${project.property("ui_lib")}-fabric")
    modApi("maven.modrinth:architectury-api:${project.property("architectury_api")}+fabric")


    // Kyori Adventure
    val adventurePlatform = project.property("adventure_platform_version")
    modImplementation(include("net.kyori:adventure-platform-fabric:$adventurePlatform")!!)

    // Kaml
    include(implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")!!)
    shaded("com.charleskorn.kaml:kaml:0.104.0")
    shaded("de.javagl:obj:0.4.0")

    // Permission API
    modImplementation("me.lucko:fabric-permissions-api:${project.property("fabric_permissions_version")}")

    // Game tests
    testImplementation("net.fabricmc:fabric-loader-junit:${project.property("fabric_loader")}")
    testImplementation(kotlin("test"))

    // Тяжелые зависимости
    val cyberiaDependencyVersion = project.property("cyberia_version")!!
    modImplementation("org.lain.cyberia:fabric:$cyberiaDependencyVersion")
    modImplementation("org.lain.cyberia:ecs-api:1.3.4")
    compileOnly("org.jetbrains.exposed:exposed-core:1.0.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:1.0.0")
    compileOnly("org.xerial:sqlite-jdbc:3.51.1.0")
    shaded("org.reflections:reflections:0.10.2")

    // Camera Overhaul
    val (cameraOverhaulVersion, cameraOverhaulMinecraftVersion) = project.property("camera_overhaul_version")
        .toString()
        .split("+")
        .let { it[0] to it[1] }
    modCompileOnly("maven.modrinth:cameraoverhaul:${cameraOverhaulVersion}-fabric+mc.$cameraOverhaulMinecraftVersion-plus")

    // API Lamb Dynamic Lights
    modCompileOnly("dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:${project.property("lambdynamiclights_version")}")
    modLocalRuntime("dev.lambdaurora.lambdynamiclights:lambdynamiclights-runtime:${project.property("lambdynamiclights_version")}")

    // API WorldEdit
    modCompileOnly("com.sk89q.worldedit:worldedit-core:${project.property("worldedit_version")}")
    modCompileOnly("com.sk89q.worldedit:worldedit-fabric-mc$minecraft_version:${project.property("worldedit_version")}")

    // Grapgene
    modImplementation("io.github.trethore:graphene-ui:1.7.2")

    // Lua
    compileOnly("org.luaj:luaj-jse:3.0.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft"))
    inputs.property("loader_version", project.property("fabric_loader"))
    inputs.property("cyberia_version", project.property("cyberia_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft")!!,
            "loader_version" to project.property("fabric_loader")!!,
            "kotlin_loader_version" to project.property("kotlin_loader")!!,
            "cyberia_version" to project.property("cyberia_version")!!
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
    options.release.set(jdkVersion)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jdkVersion.toString()))
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
}