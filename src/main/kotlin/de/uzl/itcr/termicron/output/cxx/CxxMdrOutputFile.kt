package de.uzl.itcr.termicron.output.cxx

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import org.apache.tika.mime.MimeType
import org.redundent.kotlin.xml.Node
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.time.ZonedDateTime

/**
 * CentraXX MDR file-based output. This generates XML compliant to the CentraXXExchange.xsd as of CentraXX MDR 1.9.3
 *
 * @constructor
 * implements CxxMdrOutput
 *
 * @param catalogType the catalog type to use in the generated catalog
 */
class CxxMdrOutputFile(catalogType: String) : CxxMdrOutput(catalogType) {

    /**
     * render the ValueSet vs to a XML catalog
     *
     * @param vs the ValueSet expansion
     * @return the rendered XML, as an encapsulated String
     */
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutput.MdrOutputResult =
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
            MdrOutput.MdrOutputResult(
                x.toString(
                    PrintOptions(
                        pretty = true,
                        singleLineTextElements = true
                    )
                )
            )
        }

    /**
     * this outputs XML
     *
     * @return the XML MIME type
     */
    override fun mimeType(): MimeType = StaticHelpers
        .tikaConfig
        .mimeRepository
        .forName("application/xml")

    /**
     * adds german and english designations to this node, with the same value
     *
     * @param value the value to use within the language nodes
     */
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
}


