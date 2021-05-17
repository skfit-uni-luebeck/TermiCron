package de.uzl.itcr.termicron.output

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import org.apache.tika.mime.MimeType

interface MdrOutput {
    fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult

    fun mimeType() : MimeType
}