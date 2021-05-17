package de.uzl.itcr.termicron.output.cxx

import com.lectra.koson.arr
import com.lectra.koson.obj
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutputResult

class CxxMdrOutputRest(catalogType: String) : CxxMdrOutput(catalogType) {

    override fun outputCatalog(vs: ValueSetExpansion): MdrOutputResult {
        val cxxOutput = obj {
            "caption" to obj {
                "en" to obj {
                    "name" to vs.title
                    "description" to vs.description
                }
                "de" to obj {
                    "name" to vs.title
                    "description" to vs.description
                }
            }
            //"code" to vs.title
            "code" to vs.nameUrlEncoded
            // DONE only works for the creation not the update
            //"uri" to null
            "uri" to buildCxxUrl(vs)
            "version" to vs.businessVersion
            "systemUrl" to vs.canonicalUrl
            "catalogType" to catalogType
            "entries" to arr[vs.concepts.map { concept: ValueSetExpansion.ValueSetConcept ->
                obj {
                    "caption" to obj {
                        "en" to obj {
                            "name" to concept.display
                            "description" to null
                        }
                        "de" to obj {
                            "name" to concept.display
                            "description" to null
                        }
                    }
                    //"uri" to null
                    "uri" to buildCxxUrl(vs, concept)
                    "code" to concept.code
                    "parent" to null
                    "catalogUri" to buildCxxUrl(vs)
                    "modificationTime" to null
                }
            }]
            "modificationTime" to null
        }
        return MdrOutputResult(cxxOutput.pretty())
    }

    override fun mimeType(): org.apache.tika.mime.MimeType =
        StaticHelpers.tikaConfig.mimeRepository.forName("application/json")
}