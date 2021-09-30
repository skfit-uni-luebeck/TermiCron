package de.uzl.itcr.termicron

import ca.uhn.fhir.context.FhirContext
import org.apache.tika.config.TikaConfig
import java.net.http.HttpClient
import java.time.Duration

/**
 * static helper methods/fields
 */
class StaticHelpers {

    companion object {
        /**
         * the TikaConfig, for MIME types
         */
        val tikaConfig: TikaConfig = TikaConfig.getDefaultConfig()

        /**
         * the actual HAPI FHIR context. Since these are expensive, this is the only
         * place where one is created!
         */
        val fhirContext: FhirContext = FhirContext.forR4()

        /**
         * generate a HTTP client that always uses HTTP/1.1, and may be configured
         * using the provided initializer function
         *
         * @param initializer the initializer function, e.g. for authentication
         * @return the configured HttpClient
         */
        fun httpClient(initializer: (HttpClient.Builder.() -> Unit)? = null): HttpClient =
            HttpClient.newBuilder().apply {
                version(HttpClient.Version.HTTP_1_1)
                followRedirects(HttpClient.Redirect.ALWAYS)
                initializer?.invoke(this)
            }
                .connectTimeout(Duration.ofSeconds(20))
                .build()
    }

}