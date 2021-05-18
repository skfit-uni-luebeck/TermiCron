package de.uzl.itcr.termicron

import org.slf4j.LoggerFactory

/**
 * the logger to use
 */
val logger = LoggerFactory.getLogger(TermiCron::class.java)

/**
 * "naked" main method, without Spring boot.
 *
 * @param args the command line arguments
 */
fun main(args: Array<String>) {
    TermiCronConsoleApplication(StaticHelpers.fhirContext, logger).main(args)
}
