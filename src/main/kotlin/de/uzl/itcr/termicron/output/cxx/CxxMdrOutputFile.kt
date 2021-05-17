package de.uzl.itcr.termicron.output.cxx

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutputResult
import org.apache.tika.mime.MimeType
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.time.ZonedDateTime

class CxxMdrOutputFile(catalogType: String) : CxxMdrOutput(catalogType) {
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult =
        xml("MDRDataExchange") {
            globalProcessingInstruction(
                "xml", "version" to "1.0",
                "encoding" to "UTF-8",
                "standalone" to "yes"
            )
            "Catalogs" {
                "Catalog" {
                    "Code" {
                        -vs.name
                    }
                    "Caption" {
                        this.addLanguageNodes(vs.title)
                    }
                    "Uri" {
                        -buildCxxUrl(vs)
                    }
                    "Date" {
                        -getDateTimeFormatter()
                            .format(ZonedDateTime.now())
                    }
                    "Version" {
                        -vs.businessVersion
                    }
                    "SystemUrl" {
                        -vs.canonicalUrl
                    }
                    "CatalogType" {
                        -catalogType
                    }
                    "Entries" {
                        vs.concepts.sortedBy { it.code }.forEach { concept ->
                            "CatalogEntry" {
                                "Code" {
                                    -concept.code
                                }
                                "Caption" {
                                    addLanguageNodes(concept.display)
                                }
                                "Uri" {
                                    -buildCxxUrl(vs, concept)
                                }
                            }
                        }
                    }

                }
            }
            "AttributeDomains" {}
            "AttributeValues" {}
            "RelationTypes" {}
        }.let { x ->
            MdrOutputResult(
                x.toString(
                    PrintOptions(
                        pretty = true,
                        singleLineTextElements = true
                    )
                )
            )
        }

    override fun mimeType(): MimeType = StaticHelpers
        .tikaConfig
        .mimeRepository
        .forName("application/xml")
}

private fun Node.addLanguageNodes(value: String) {
    listOf("en", "de").forEach { lang ->
        "Entry" {
            "Language" {
                -lang
            }
            "Name" {
                -value
            }
        }
    }
}
