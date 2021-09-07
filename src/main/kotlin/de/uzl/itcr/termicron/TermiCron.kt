package de.uzl.itcr.termicron

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.util.*

const val springProfileTermicron = "termicron"

/**
 * wrap TermiCron's CLI as a Spring Boot application
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class TermiCron(
    @Value("\${spring.profiles.active}")
    val activeProfile: String
) {
    /**
     * the base application logger
     */
    @Bean
    fun log(): Logger = LoggerFactory.getLogger(TermiCron::class.java)

    /**
     * the HAPI FHIR context, which will be initialized exactly once
     */
    @Bean
    fun fhirContext() = StaticHelpers.fhirContext

    /**
     * run the TermiCron CLI main method using the provided arguments
     */
    @Bean
    fun init() = CommandLineRunner { args ->
        val profiles = activeProfile.lowercase(Locale.getDefault())
        if (profiles.contains(springProfileTermicron)) TermiCronConsoleApplication(fhirContext(), log()).main(args)
        //bundle builder controller starts automatically, if the profile is provided
    }

}

/**
 * start the TermiCron app via Spring Boot
 *
 * @param args the command line arguments
 */
fun main(args: Array<String>) {
    runApplication<TermiCron>(*args)
}
