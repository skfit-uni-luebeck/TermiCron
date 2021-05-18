package de.uzl.itcr.termicron

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * the base application logger
 */
private val log: Logger = LoggerFactory.getLogger(TermiCron::class.java)

/**
 * the HAPI FHIR context, which will be initialized exactly once on assignment
 */
private val fhirContext = StaticHelpers.fhirContext

/**
 * wrap TermiCron's CLI as a Spring Boot application
 */
@SpringBootApplication
class TermiCron {

    /**
     * run the TermiCron CLI main method using the provided arguments
     */
    @Bean
    fun init() = CommandLineRunner { args ->
        TermiCronConsoleApplication(fhirContext, log).main(args)
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
