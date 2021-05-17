@file:Suppress("HttpUrlsUsage")

package de.uzl.itcr.termicron.output.samply

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import de.uzl.itcr.termicron.output.MdrOutputResult
import org.apache.tika.mime.MimeType
import org.redundent.kotlin.xml.xml

class SamplyMdrOutput : MdrOutput {
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult {

        val samplyOutput = xml("catalog") {
            xmlns = "http://schema.samply.de/mdr/common"
            "definitions" {
                "definition" {
                    attribute("lang", "en")
                    "designation"{
                        -vs.title
                    }
                    if (vs.description != null)
                        "definition" {
                            -vs.description
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
        return MdrOutputResult(
            result = samplyOutput.toString(prettyFormat = true)
        )
    }

    override fun mimeType(): MimeType = StaticHelpers.tikaConfig.mimeRepository.forName("application/xml")
}