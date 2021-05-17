package de.uzl.itcr.termicron.catalogmodel

import java.net.URLEncoder

/**
 * encapsulated FHIR ValueSet expansion
 *
 * @property canonicalUrl the canonical URL of the ValueSet
 * @property name the name of the ValueSet
 * @property title the title of the ValueSet
 * @property description the human-readable description of the VS
 * @property versionId the meta.versionId
 * @property businessVersion the version
 * @property concepts the list of contained concepts
 */
data class ValueSetExpansion(
    val canonicalUrl: String,
    val name: String,
    val title: String,
    val description: String?,
    val versionId: String,
    val businessVersion: String,
    val concepts: List<ValueSetConcept>
) {
    /**
     * class for concepts in the ValueSetExpansion
     *
     * @property system every concept comes from =1 CS
     * @property code the code within the CS
     * @property display the display string of the CS
     */
    data class ValueSetConcept(
        val system: String,
        val code: String,
        val display: String,
    ) {
        /**
         * the URL-encodes code string, which might be required for some MDR operations
         */
        val codeUrlEncoded: String get() = URLEncoder.encode(code, "utf-8")
    }

    /**
     * the URL-encodes title string, which might be required for some MDR operations
     */
    val titleUrlEncoded: String get() = URLEncoder.encode(title, "utf-8")

    /**
     * the URL-encodes name string, which might be required for some MDR operations
     */
    val nameUrlEncoded: String get() = URLEncoder.encode(name, "utf-8")

    /**
     * the URL-encodes businessVersion string, which might be required for some MDR operations
     */
    val businessVersionUrlEncoded: String get() = URLEncoder.encode(businessVersion, "utf-8")
}

