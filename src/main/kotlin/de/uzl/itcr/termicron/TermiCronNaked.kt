package de.uzl.itcr.termicron

import org.slf4j.LoggerFactory

/**
 * "naked" main method, without Spring boot.
 *
 * @param args the command line arguments
 */
fun main(args: Array<String>) {
    val fhirContext = StaticHelpers.fhirContext
    val logger = LoggerFactory.getLogger(TermiCronConsoleApplication::class.java)

    TermiCronConsoleApplication(fhirContext, logger).main(args)
}
