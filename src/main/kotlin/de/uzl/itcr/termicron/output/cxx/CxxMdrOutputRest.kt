package de.uzl.itcr.termicron.output.cxx

import com.lectra.koson.arr
import com.lectra.koson.obj
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput

/**
 * CentraXX MDR REST-based output. This generates JSON compliant to the API documentation as of CentraXX MDR 1.9.3
 *
 * @constructor
 * implements CxxMdrOutput
 *
 * @param catalogType the catalog type to use in the generated catalog
 */
class CxxMdrOutputRest(catalogType: String) : CxxMdrOutput(catalogType) {

    /**
     * render the ValueSet vs to a JSON catalog
     *
     * @param vs the ValueSet expansion
     * @return the rendered JSON, as an encapsulated String
     */
    override fun outputCatalog(vs: ValueSetExpansion): MdrOutput.MdrOutputResult {
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
            "code" to vs.nameUrlEncoded
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
                    "uri" to buildCxxUrl(vs, concept)
                    "code" to concept.code
                    "parent" to null
                    "catalogUri" to buildCxxUrl(vs)
                    "modificationTime" to null
                }
            }]
            "modificationTime" to null
        }
        return MdrOutput.MdrOutputResult(cxxOutput.pretty())
    }

    /**
     * this outputs JSON
     *
     * @return the JSON MIME type
     */
    override fun mimeType(): org.apache.tika.mime.MimeType =
        StaticHelpers.tikaConfig.mimeRepository.forName("application/json")
}