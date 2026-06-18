plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.grip"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
}

// Declared explicitly so the Spring Boot plugin does not scan compiled classes
// for a main method. The scan parses every .class file, and on macOS external
// drives the AppleDouble "._*" sidecars are not valid bytecode — scanning them
// throws IllegalArgumentException from spring-asm.
springBoot {
    mainClass = "com.grip.pipeline.PipelineServiceApplication"
}

checkstyle {
    toolVersion = "10.14.2"
    configFile = file("config/checkstyle/checkstyle.xml")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
