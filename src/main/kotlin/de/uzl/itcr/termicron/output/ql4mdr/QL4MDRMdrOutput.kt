package de.uzl.itcr.termicron.output.ql4mdr

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import de.uzl.itcr.termicron.output.MdrOutputResult
import org.apache.tika.mime.MimeType

class QL4MDRMdrOutput : MdrOutput {

    override fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult {
        val ql4MdrMutation = ql4mdr {
            mutation {
                mutationNode("createConceptSystem", listOf("name")) {
                    attributes {
                        +("name" to vs.name)
                        +("uri" to vs.canonicalUrl)
                        +("version" to vs.businessVersion)
                        vs.concepts.forEach { c ->
                            listAttribute("concepts") {
                                +("uri" to "${c.system}#${c.code}")
                                +("prefLabel" to c.code)
                                +("altLabel" to "")
                                +("definition" to c.display)
                            }
                        }
                    }
                }
            }
        }
        return MdrOutputResult(ql4MdrMutation.toString())
    }

    override fun mimeType(): MimeType = StaticHelpers.tikaConfig.mimeRepository.forName("application/json")

}