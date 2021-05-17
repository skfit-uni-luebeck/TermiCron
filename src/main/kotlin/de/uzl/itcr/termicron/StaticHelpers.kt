package de.uzl.itcr.termicron

import ca.uhn.fhir.context.FhirContext
import org.apache.tika.config.TikaConfig
import java.net.http.HttpClient

class StaticHelpers {

    companion object {
        val tikaConfig: TikaConfig = TikaConfig.getDefaultConfig()
        val fhirContext: FhirContext = FhirContext.forR4()

        fun httpClient(initializer: (HttpClient.Builder.() -> Unit)? = null): HttpClient = HttpClient.newBuilder().apply {
            version(HttpClient.Version.HTTP_1_1)
            initializer?.invoke(this)
        }.build()

    }

}