plugins {
    id("fabric-loom") version "1.8-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    implementation("org.apache.httpcomponents:httpclient:4.5.10")
    include("org.apache.httpcomponents:httpclient:4.5.10")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    archiveClassifier.set("") 
    from("LICENSE") {
        rename { "${it}_${project.property("archives_base_name")}" }
    }
}

// DISABLE REMAPPING - This keeps the Mojang names in the jar
tasks.remapJar {
    enabled = false
}

// Ensure the un-remapped jar is what gets built
tasks.build {
    dependsOn(tasks.jar)
    doLast {
        copy {
            from("build/devlibs/Skyblock-Flips-Client-Side-1.2.jar")
            into("build/libs")
        }
    }
}
