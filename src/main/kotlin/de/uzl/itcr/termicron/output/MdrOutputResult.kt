package de.uzl.itcr.termicron.output

/**
 * represents the status of an MDR catalog output operation
 */
data class MdrOutputResult(
        val result: String,
        val success: Boolean = true
)