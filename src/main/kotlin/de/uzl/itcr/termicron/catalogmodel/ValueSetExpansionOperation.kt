package de.uzl.itcr.termicron.catalogmodel

import de.uzl.itcr.termicron.ingest.TerminologyConversionError

data class ValueSetExpansionOperation(
    val canonicalUrl: String,
    val valueSetExpansion: ValueSetExpansion? = null,
    val conversionError: TerminologyConversionError? = null
)