plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.21"
    id("org.jetbrains.kotlin.kapt") version "1.6.21"
    id("io.micronaut.application") version "3.6.3"
}

version = "0.1"
group = "org.example"

val kotlinVersion=project.properties.get("kotlinVersion")
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    implementation("io.micronaut.kafka:micronaut-kafka:4.5.0")

    testImplementation(kotlin("test"))
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka:1.17.6") // Yes
    testImplementation("org.testcontainers:testcontainers:1.17.6") // Yes
}

java {
    sourceCompatibility = JavaVersion.toVersion("17")
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("org.example.*")
    }
}
