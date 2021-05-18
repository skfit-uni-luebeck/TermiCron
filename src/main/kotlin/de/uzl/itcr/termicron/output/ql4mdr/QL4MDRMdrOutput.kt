package de.uzl.itcr.termicron.output.ql4mdr

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import org.apache.tika.mime.MimeType

/**
 * render a catalog to a QL4MDR mutation via the custom DSL
 */
class QL4MDRMdrOutput : MdrOutput {

    /**
     * render the provided ValueSet to a GraphQL query that looks like this:
     *
     * ```
     * mutation {
     *   createConceptSystem (
     *     ...
     *     concepts: [
     *       {
     *         ...
     *       }
     *     ]
 *       ) {
     *     name
     *   }
     * }
     * ```
     *
     * @param vs the ValueSet to render
     * @return the encapsulated String
     */
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutput.MdrOutputResult {
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
        return MdrOutput.MdrOutputResult(ql4MdrMutation.toString())
    }

    /**
     * this returns GraphQL
     *
     * @return the GraphQL MIME type
     */
    override fun mimeType(): MimeType = StaticHelpers.tikaConfig
        .mimeRepository.forName("application/graphql")

}