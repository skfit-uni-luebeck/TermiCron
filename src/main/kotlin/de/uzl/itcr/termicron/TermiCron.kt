package de.uzl.itcr.termicron

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import java.util.*
import kotlin.system.exitProcess


/**
 * wrap TermiCron's CLI as a Spring Boot application
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class TermiCron {

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

}

/**
 * start the TermiCron app via Spring Boot
 *
 * If the "bundlebuilder" argument is provided, the bundle builder web app will start.
 * Otherwise, the TermiCron command line app takes over parsing the command line arguments, and bundle builder is inhibited
 *
 * @param args the command line arguments
 */
fun main(args: Array<String>) {
    val app = SpringApplicationBuilder(TermiCron::class.java)
    val fhirContext = StaticHelpers.fhirContext
    val logger = LoggerFactory.getLogger(TermiCron::class.java)
    val termiCronApp = TermiCronConsoleApplication(fhirContext, logger)
    val firstArg = when {
        args.isEmpty() -> null
        else -> args[0].lowercase(Locale.getDefault()).replace("-", "")
    }
    when (firstArg) {
        null, "help" -> {
            println(
                """
                TermiCron converts FHIR terminology resources to meta data repository definitions.
                
                To run the TermiCron bundle builder web application, run TermiCron with the argument `bundlebuilder` 
                (e.g. java -jar termicron.jar bundlebuilder).
                
                The bundle builder will be available by default at http://localhost:8080 (you can override the port
                using standard Spring Boot mechanisms as needed).
                
                If you provide any other argument, TermiCron will handle argument parsing and run on the command line:
                
            """.trimIndent()
            )
            termiCronApp.main(arrayOf("--help"))
            exitProcess(0)
        }
        "bundlebuilder" -> {
            // this allows the bundle builder web app to run
            app.web(WebApplicationType.SERVLET)
            app.run(*args)
        }
        else -> {
            // this inhibits running the web app, and hands over parsing to the CLI app
            app.web(WebApplicationType.NONE)
            termiCronApp.main(args)
        }
    }
    app.run(*args)
}
