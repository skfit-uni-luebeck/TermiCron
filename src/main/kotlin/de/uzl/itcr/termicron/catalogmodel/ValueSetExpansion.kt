package de.uzl.itcr.termicron.catalogmodel

import java.net.URLEncoder

data class ValueSetExpansion(
    val canonicalUrl: String,
    val name: String,
    val title: String,
    val description: String?,
    val versionId: String,
    val businessVersion: String,
    val concepts: List<ValueSetConcept>
) {
    data class ValueSetConcept(
        val system: String,
        val code: String,
        val display: String,
        //val parents: List<ValueSetConcept>
    ) {
        val codeUrlEncoded: String get() = URLEncoder.encode(code, "utf-8")
    }

    val titleUrlEncoded: String get() = URLEncoder.encode(title, "utf-8")
    val nameUrlEncoded: String get() = URLEncoder.encode(name, "utf-8")
    val businessVersionUrlEncoded: String get() = URLEncoder.encode(businessVersion, "utf-8")
}

