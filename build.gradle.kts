import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName.set(providers.gradleProperty("archives_base_name").get())
}

repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/qbrp/cyberia")
        credentials {
            username = findProperty("gpr.user") as String?
            password = findProperty("gpr.key") as String?
        }
    }
    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven")
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://maven.logandark.net")
    maven("https://maven.gegy.dev")
    maven("https://maven.enginehub.org/repo/")
    maven("https://maven.wispforest.io/releases/2412")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()
    accessWidenerPath = file("src/client/resources/engine.classtweaker")

    mods {
        register("engine") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

val shaded by configurations.creating

dependencies {
    fun shaded(dependency: Any) {
        implementation(dependency)
        add(shaded.name, dependency)
    }

    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.bundles.fabric)

    modImplementation(libs.yaml)
    modImplementation(libs.ui)
    modImplementation(libs.architectury)
    modImplementation(libs.adventure) {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api-bom")
    }
    modImplementation(libs.permissions) {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api-bom")
    }
    modImplementation(libs.female.gender)
    modLocalRuntime(libs.lambdynlights.runtime) {
        exclude(group = "net.fabricmc", module = "fabric-loader")
    }
    modCompileOnly(libs.bundles.compat)

    compileOnly(libs.cyberia.ecs.api)
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.luaj)
    compileOnly(libs.reflections)
    compileOnly(libs.obj)
    compileOnly(libs.kaml)
    compileOnly(libs.protobuf)
    compileOnly(libs.credential.secure.storage)
    modImplementation("org.lain.cyberia:fabric:1.13")
    testImplementation(libs.fabric.loader.junit)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabric.loader.get(),
        "kotlin_loader_version" to libs.versions.fabric.kotlin.get(),
    )

    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.shadowJar {
    exclude("kotlin/**")
    exclude("META-INF/kotlin*")
    exclude("*kotlin")
    from(sourceSets.main.get().output)
    from(sourceSets["client"].output)
    configurations = listOf(shaded)
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    val projectName = project.name
    inputs.property("projectName", projectName)

    from("LICENSE.txt") {
        rename { "${it}_$projectName" }
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
}
