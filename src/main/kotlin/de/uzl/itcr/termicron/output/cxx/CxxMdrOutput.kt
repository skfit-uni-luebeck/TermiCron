package de.uzl.itcr.termicron.output.cxx

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import java.time.format.DateTimeFormatter

/**
 * an abstract class for handling output to a CentraXX MDR.
 * Implementing classes provide file and REST output.
 *
 * @property catalogType the catalog type to use in the generated catalog
 */
abstract class CxxMdrOutput(val catalogType: String) : MdrOutput {

    companion object {
        /**
         * the prefix that CentraXX MDR uses for generated URNs
         */
        const val cxxPrefix = "tag:kairos.de,2017"
    }

    /**
     * build a CentraXX MDR url to the schema that CXX uses. First, the prefix, then a path (depending on whether a code is passed),
     * then the name and version of the catalog, then the code if present
     *
     * @param vs the VS to build the URN for
     * @param code the concept that is referenced, or null
     * @return the Url as used by CentraXX MDR
     */
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

    /**
     * formatter for dates as required by CentraXX MDR
     *
     * @return the configured DateTimeFormatter with the correct pattern
     */
    @Suppress("SpellCheckingInspection")
    protected fun getDateTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

}
