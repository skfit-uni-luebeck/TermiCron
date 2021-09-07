import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.4.5"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.4.32"
    kotlin("plugin.spring") version "1.4.32"
    id("application")
}

application {
    mainClass.set("de.uzl.itcr.termicron.TermiCronKt")
}

group = "de.uzl.itcr"
version = "1.0.1"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val hapiVersion = "5.2.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.7.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-client-okhttp:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation:$hapiVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-validation-resources-r4:$hapiVersion")
    implementation("org.redundent:kotlin-xml-builder:1.7.2")
    implementation("com.lectra:koson:1.1.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.github.ajalt.clikt:clikt:3.1.0")
    implementation("org.orienteer.jnpm:jnpm:1.1")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("org.rauschig:jarchivelib:1.1.0")
    implementation("org.apache.tika:tika-core:1.26")
    implementation("com.google.oauth-client:google-oauth-client:1.31.2")
    implementation("com.google.oauth-client:google-oauth-client-java6:1.31.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.31.2")
    implementation("com.google.http-client:google-http-client:1.39.2")
    implementation("com.google.http-client:google-http-client-gson:1.39.2")
    //retrofit dependencies added to override unversioned dependency on Retrofit2 of jnpm.
    // 2.9.0 is not great with Java 11 due to illegal reflective operations!
    implementation("com.squareup.retrofit2:retrofit:2.7.2")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.7.2")
    implementation("com.squareup.retrofit2:converter-jackson:2.7.2")
    implementation("ninja.sakib:kotlin-jsonq:v0.2")

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
