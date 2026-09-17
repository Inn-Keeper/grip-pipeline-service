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

// Spring Boot manages Testcontainers (1.19.8 on Boot 3.3) and overrides versions
// pinned on individual modules, so set the managed version. Releases before
// 1.21.4 use a Docker API that Docker Engine 29 refuses, and the
// disabledWithoutDocker ITs then skip silently instead of failing.
extra["testcontainers.version"] = "1.21.4"

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
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
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
    // @SpringBootTest scans compiled classes too and trips over the same "._*"
    // sidecars (see mainClass above); skip files Spring cannot parse.
    systemProperty("spring.classformat.ignore", "true")
}
