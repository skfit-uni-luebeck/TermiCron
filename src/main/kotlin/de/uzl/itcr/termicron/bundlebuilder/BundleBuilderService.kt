package de.uzl.itcr.termicron.bundlebuilder

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.ingest.FhirUtilities
import de.uzl.itcr.termicron.ingest.cleanUri
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.URI
import java.util.*

const val termiCronProfile = "http://fhir.imi.uni-luebeck.de/StructureDefinition/TermiCron%20Bundle"

@Service
class BundleBuilderService(
    @Autowired val fhirContext: FhirContext,
) {
    private val log = LoggerFactory.getLogger(BundleBuilderService::class.java)

    private val fhirUtilities by lazy { FhirUtilities(StaticHelpers.httpClient(), fhirContext) }

    private fun validateServerUrl(uriString: String): FhirUtilities.KnownFhirServer? = try {
        fhirUtilities.getServerSoftware(uriString.cleanUri())
    } catch (e: Exception) {
        null
    }

    private inline fun <reified T : MetadataResource> gatherResourcesForResourceTypeFromServer(uriString: String): List<Reference>? {
        if (validateServerUrl(uriString) == null) return null
        val requestUrl = URI.create("${uriString.cleanUri()}${T::class.simpleName}")
        log.debug("Requesting from $requestUrl")
        val resourceBundle = fhirUtilities.executeFhirStatementParsing<Bundle>(requestUrl)
        val references = resourceBundle.entry?.map { bundleEntryComponent ->
            val r = bundleEntryComponent.resource
            val displayString = "${bundleEntryComponent.resource.fhirType()}: " + when (r) {
                is CodeSystem -> "${r.url} (${r.title}) version ${r.version}"
                is ValueSet -> "${r.url} (${r.title}) version ${r.version}"
                else -> r.id
            }
            Reference(r).apply {
                reference = bundleEntryComponent.fullUrl
                id = UUID.randomUUID().toString()
                type = r.fhirType()
                display = displayString
            }
        }
        return references?.filter {
            //we only consider CS with valueSet URL
            when {
                it.resource is CodeSystem && (it.resource as CodeSystem).valueSet == null -> false
                else -> it.type in listOf("CodeSystem", "ValueSet")
            }
        }?.sortedBy { it.display }

    }

    fun gatherCsVsForUrlList(uriList: List<String>) = uriList.map { uri ->
        ReferenceList(
            serverEndpoint = uri,
            codeSystems = gatherResourcesForResourceTypeFromServer<CodeSystem>(uri) ?: listOf(),
            valueSets = gatherResourcesForResourceTypeFromServer<ValueSet>(uri) ?: listOf()
        )
    }

    fun buildBundle(resources: List<ReferenceList>, selectedIdList: List<String>, bundleId: String): Bundle {
        fun filterResources(selector: (ReferenceList) -> List<Reference>) =
            resources.map(selector).flatten().filter { it.id in selectedIdList }

        fun linksForBundleEntry(resource: IBaseResource): List<Bundle.BundleLinkComponent> {
            val links = when (resource) {
                is CodeSystem -> mapOf(
                    "canonical" to resource.valueSet,
                    "CodeSystem" to resource.url
                )
                is ValueSet -> mapOf(
                    "canonical" to resource.url,
                    "ValueSet" to resource.url
                )
                else -> throw IllegalArgumentException("resource type ${resource.fhirType()} is not supported!")
            }
            return links.map { Bundle.BundleLinkComponent(StringType(it.key), UrlType(it.value)) }
        }

        val selectedCs = filterResources { it.codeSystems }
        val selectedVs = filterResources { it.valueSets }

        val selectedResources = selectedCs + selectedVs

        val bundle = Bundle().apply {
            type = Bundle.BundleType.COLLECTION
            id = bundleId
            entry = selectedResources.map { ref ->
                Bundle.BundleEntryComponent().apply {
                    fullUrl = ref.reference
                    link.addAll(linksForBundleEntry(ref.resource))
                }
            }
            total = entry.size
            meta.addProfile(termiCronProfile)
        }
        return bundle
    }

    @Suppress("unused")
    data class ReferenceList(
        val serverEndpoint: String,
        val codeSystems: List<Reference>,
        val valueSets: List<Reference>
    ) {
        val codeSystemsSize: Int get() = codeSystems.size
        val valueSetsSize: Int get() = valueSets.size
    }

}