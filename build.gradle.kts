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

    modImplementation(libs.graphene)
    modImplementation(libs.yaml)
    modImplementation(libs.ui)
    modImplementation(libs.architectury)
    modImplementation(libs.adventure)
    modImplementation(libs.cyberia)
    modImplementation(libs.cyberia.ecs.api)
    modImplementation(libs.permissions)
    modImplementation(libs.female.gender)
    modLocalRuntime(libs.lambdynlights.runtime)
    modCompileOnly(libs.bundles.compat)

    compileOnly(libs.bundles.libraries)
    testImplementation(libs.fabric.loader.junit)

    implementation(libs.protobuf)
    include(libs.protobuf)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val version = version
    inputs.property("version", version)
    inputs.property("minecraft_version", libs.versions.minecraft.get())
    inputs.property("loader_version", libs.versions.fabric.loader.get())
    inputs.property("kotlin_loader_version", libs.versions.fabric.kotlin.get())
    inputs.property("cyberia_version", libs.versions.cyberia.version.get())

    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
            "kotlin_loader_version" to libs.versions.fabric.kotlin.get(),
            "cyberia_version" to libs.versions.cyberia.version.get(),
        )
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
