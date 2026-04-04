plugins {
    java
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    id("com.github.spotbugs") version "6.1.3"
    jacoco
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // MyBatis
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // Webjars
    implementation("org.webjars:bootstrap:5.3.3")
    implementation("org.webjars.npm:htmx.org:2.0.4")
    implementation("org.webjars.npm:alpinejs:3.14.8")

    // DevTools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.4")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // ArchUnit
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")

    // SpotBugs annotations
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.9.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Checkstyle
checkstyle {
    toolVersion = "10.21.4"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true
}

// SpotBugs
spotbugs {
    ignoreFailures = true
    excludeFilter = file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports.create("html") {
        required = true
    }
    reports.create("xml") {
        required = false
    }
}

// JaCoCo
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Boot
springBoot {
    mainClass = "com.example.cargotracker.CargoTrackerApplication"
}
