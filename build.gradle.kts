import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val springVersion = "2.5.4"

plugins {
    id("org.springframework.boot") version "2.5.4"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.5.30"
    kotlin("plugin.spring") version "1.5.30"
    id("application")
    kotlin("kapt") version "1.5.30"
}

application {
    mainClass.set("de.uzl.itcr.termicron.TermiCronKt")
}

group = "de.uzl.itcr"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val hapiVersion = "5.2.0"

val springSessionVersion = "2.5.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter:$springVersion")
    implementation("org.springframework.boot:spring-boot-starter-mustache:$springVersion")
    implementation("org.springframework.boot:spring-boot-starter-web:$springVersion")
    implementation("org.springframework.session:spring-session-core:$springSessionVersion")
    developmentOnly("org.springframework.boot:spring-boot-devtools:$springVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springVersion")
    testImplementation(platform("org.junit:junit-bom:5.7.1"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-client-okhttp:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation-resources-r4:$hapiVersion")
    implementation("org.redundent:kotlin-xml-builder:1.7.3")
    implementation("com.lectra:koson:1.1.0")
    implementation("com.google.code.gson:gson:2.8.8")
    implementation("com.github.ajalt.clikt:clikt:3.2.0")
    implementation("org.orienteer.jnpm:jnpm:1.1")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.rauschig:jarchivelib:1.2.0")
    implementation("org.apache.tika:tika-core:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client:1.32.1")
    implementation("com.google.oauth-client:google-oauth-client-java6:1.32.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.32.1")
    implementation("com.google.http-client:google-http-client:1.40.0")
    implementation("com.google.http-client:google-http-client-gson:1.40.0")

    //retrofit dependencies added to override unversioned dependency on Retrofit2 of jnpm.
    // 2.9.0 is not great with Java 11 due to illegal reflective operations!
    @Suppress("GradlePackageUpdate")
    implementation("com.squareup.retrofit2:retrofit:2.7.2")
    @Suppress("GradlePackageUpdate")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.7.2")
    @Suppress("GradlePackageUpdate")
    implementation("com.squareup.retrofit2:converter-jackson:2.7.2")
    implementation("ninja.sakib:kotlin-jsonq:v0.2")
    implementation("com.googlecode.lanterna:lanterna:3.1.1")

    kapt("org.springframework.boot:spring-boot-configuration-processor:$springVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springVersion")
}



tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configurations.all {
    exclude("org.slf4j", "slf4j-log4j12")
    //this dependency is pulled in by kotlin-jsonq, but it's deprecated.
    //stdlib-jdk8 is already on the classpath and compatible!
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jre8")
}
