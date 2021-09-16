@file:Suppress("HttpUrlsUsage")

package de.uzl.itcr.termicron.output.samply

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import org.apache.tika.mime.MimeType
import org.redundent.kotlin.xml.xml

/**
 * render catalogs in Samply XML format
 */
class SamplyMdrOutput : MdrOutput {
    /**
     * render the provided VS to a Samply MDR catalog via the provided schema
     *
     * @param vs the ValueSet to render
     * @return the catalog as an encapsulated String
     */
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutput.MdrOutputResult {

        val samplyOutput = xml("catalog") {
            xmlns = "http://schema.samply.de/mdr/common"
            "definitions" {
                "definition" {
                    attribute("lang", "en")
                    "designation"{
                        -vs.title
                    }
                    "definition" {
                        if (vs.description != null) -vs.description else -"missing description"
                    }
                }
            }

            "slots"{
                "slot"{
                    "key" {
                        -"fhir.ValueSet.url"
                    }
                    "value" {
                        -vs.canonicalUrl
                    }
                }
                "slot"{
                    "key" {
                        -"fhir.ValueSet.version"
                    }
                    "value" {
                        -vs.businessVersion
                    }
                }
            }

            for (code in vs.concepts) {
                "code" {
                    attribute("code", code.code)
                    attribute("isValid", true)
                    "definitions" {
                        "definition" {
                            attribute("lang", "en")
                            "designation"{
                                -code.code
                            }
                            "definition" {
                                -code.display
                            }
                        }
                    }
                }
            }
        }
        return MdrOutput.MdrOutputResult(
            result = samplyOutput.toString(prettyFormat = false)
        )
    }

    /**
     * this class returns XML
     *
     * @return the XML MIME type
     */
    override fun mimeType(): MimeType = StaticHelpers.tikaConfig.mimeRepository.forName("application/xml")
}