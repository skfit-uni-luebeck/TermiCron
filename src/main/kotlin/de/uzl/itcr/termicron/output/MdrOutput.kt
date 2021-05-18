package de.uzl.itcr.termicron.output

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import org.apache.tika.mime.MimeType

/**
 * an output renderer for a catalog/concept system from a FHIR ValueSet expansion (model)
 *
 */
interface MdrOutput {

    /**
     * render the VS to an encapsulated string pursuant to the respective format
     *
     * @param vs the expanded VS
     * @return the encapsulated result
     */
    fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult

    /**
     * tell consumers what MIME type this class generates, e.g. for writing to file with
     * the correct extension
     *
     * @return the Tika MIME type
     */
    fun mimeType() : MimeType

    /**
     * represents the status of an MDR catalog output operation
     *
     * @property result the catalog, as a String
     * @property success whether the conversion was successful
     */
    data class MdrOutputResult(
        val result: String?,
        val success: Boolean = true
    )
}

