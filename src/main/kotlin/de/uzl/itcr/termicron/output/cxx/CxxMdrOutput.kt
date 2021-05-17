package de.uzl.itcr.termicron.output.cxx

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import java.time.format.DateTimeFormatter

abstract class CxxMdrOutput(val catalogType: String) : MdrOutput {

    companion object {
        const val cxxPrefix = "tag:kairos.de,2017"
    }

    protected fun buildCxxUrl(vs: ValueSetExpansion, code: ValueSetExpansion.ValueSetConcept? = null): String =
        StringBuilder(cxxPrefix)
            .append(":")
            .append(if (code != null) "mdr/catentry" else "mdr/catalog")
            .append(":")
            .append(vs.title)
            .append(":")
            .append(vs.businessVersionUrlEncoded)
            .append(code?.let { ":${it.codeUrlEncoded}" } ?: "")
            .toString()

    @Suppress("SpellCheckingInspection")
    protected fun getDateTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

}
