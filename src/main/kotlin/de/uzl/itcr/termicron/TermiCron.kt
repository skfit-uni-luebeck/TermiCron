package de.uzl.itcr.termicron

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

private val log: Logger = LoggerFactory.getLogger(TermiCron::class.java)
private val fhirContext = StaticHelpers.fhirContext

@SpringBootApplication
class TermiCron {

    @Bean
    fun init() = CommandLineRunner { args ->
        TermiCronConsoleApplication(fhirContext, log).main(args)
    }

}

fun main(args: Array<String>) {
    runApplication<TermiCron>(*args)
}
