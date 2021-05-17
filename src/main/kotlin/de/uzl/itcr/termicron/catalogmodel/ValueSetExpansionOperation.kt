package de.uzl.itcr.termicron.catalogmodel

import de.uzl.itcr.termicron.ingest.TerminologyConversionError

/**
 * encapsulate the result from an $expand operation against a FHIR Terminology Server
 *
 * @property canonicalUrl the canonical of the ValueSet that is expanded
 * @property valueSetExpansion: the expanded VS, if the operation was successful
 * @property conversionError the conversion errors, if the operation was not successful
 */
data class ValueSetExpansionOperation(
    val canonicalUrl: String,
    val valueSetExpansion: ValueSetExpansion? = null,
    val conversionError: TerminologyConversionError? = null
)