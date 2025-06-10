plugins {
    kotlin("jvm") version "2.1.20"
}

group = "org.bread_experts_group"
version = "1.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://107-132-83-172.lightspeed.snantx.sbcglobal.net/") }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.bread_experts_group:bread_server_lib-code:2.22.2")
}

tasks.test {
    useJUnitPlatform()
}
tasks.jar.configure {
    manifest {
        attributes(mapOf("Main-Class" to "org.bread_experts_group.application_carpool.client.CarpoolCLIMainKt"))
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
}

kotlin {
    jvmToolchain(21)
}