package de.uzl.itcr.termicron.ingest.worker

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation
import de.uzl.itcr.termicron.ingest.FhirIngestProvider
import de.uzl.itcr.termicron.ingest.TerminologyConversionError
import de.uzl.itcr.termicron.ingest.FhirServerError
import de.uzl.itcr.termicron.ingest.getCanonicalLink
import org.hl7.fhir.r4.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FhirValueSetConverter(
    private val ingestProvider: FhirIngestProvider
) {

    private val logger: Logger = LoggerFactory.getLogger(FhirValueSetConverter::class.java)

    fun convertBundleEntry(
        bundleEntryComponent: Bundle.BundleEntryComponent,
        bundle: Bundle
    ): ValueSetExpansionOperation =
        when (val resourceType = resourceTypeForBundleComponent(bundleEntryComponent)) {
            ResourceType.CodeSystem -> convertCodeSystem(bundleEntryComponent, bundle)
            ResourceType.ValueSet -> convertValueSet(bundleEntryComponent, bundle)
            else -> throw UnsupportedResourceTypeException("the resource type ${resourceType.name} is not supported!")
        }

    private fun resourceTypeForBundleComponent(bundleEntryComponent: Bundle.BundleEntryComponent): ResourceType {
        when {
            bundleEntryComponent.hasLink() -> {
                val linkNames = bundleEntryComponent.link.map { it.relation.toLowerCase() }
                return when {
                    "codesystem" in linkNames -> ResourceType.CodeSystem
                    "valueset" in linkNames -> ResourceType.ValueSet
                    else -> throw MissingLinkException("no link codesystem/valueset in bundle entry")
                }
            }
            else -> throw MissingLinkException("no link for bundle entry!")
        }
    }

    private fun convertValueSet(
        bundleEntryComponent: Bundle.BundleEntryComponent,
        bundle: Bundle
    ): ValueSetExpansionOperation {
        val valueSet = this.ingestProvider.retrieveResourceByResourceCanonical(
            bundleEntryComponent.getCanonicalLink(),
            bundle
        ) as ValueSet
        val expandedValueSet = when {
            !valueSet.hasExpansion() && !ingestProvider.supportsExpansion() -> {
                throw MissingExpansionException("there is no expansion for ValueSet '${valueSet.name}' (canonical: ${valueSet.url}")
            }
            !valueSet.hasExpansion() -> try {
                ingestProvider.expandValueSet(valueSet).also {
                    logger.debug("expanded ValueSet '${it.name}', ${it.expansion.contains.count()} concepts in expansion.contains")
                }
            } catch (e: TerminologyConversionError) {
                logger.error("Error when expanding ValueSet '${valueSet.name}': ${e.message}")
                if (e is FhirServerError) {
                    val errorMessage = e.outcome?.issue
                        ?.joinToString("; ") { it.diagnostics }
                        ?: "unknown error"
                    logger.error("Error message(-s) from terminology server: $errorMessage")
                }
                return ValueSetExpansionOperation(
                    canonicalUrl = valueSet.url,
                    valueSetExpansion = null,
                    conversionError = e
                )
            }
            else -> valueSet.also {
                logger.debug("ValueSet '${valueSet.name}' is already expanded.")
            }
        }
        val concepts = expandedValueSet.expansion.contains.map {
            ValueSetExpansion.ValueSetConcept(
                system = it.system,
                code = it.code,
                display = it.display
            )
        }
        return ValueSetExpansionOperation(
            expandedValueSet.url,
            ValueSetExpansion(
                canonicalUrl = expandedValueSet.url,
                name = expandedValueSet.name,
                title = expandedValueSet.title,
                description = expandedValueSet.description,
                versionId = expandedValueSet.meta.versionId ?: "1",
                businessVersion = expandedValueSet.version,
                concepts = concepts
            )
        )

    }

    private fun convertCodeSystem(
        bundleEntryComponent: Bundle.BundleEntryComponent,
        bundle: Bundle
    ): ValueSetExpansionOperation {
        val codeSystem = this.ingestProvider.retrieveResourceByResourceCanonical(
            bundleEntryComponent.getCanonicalLink(),
            bundle
        ) as CodeSystem
        if (!codeSystem.hasValueSet()) {
            val error =
                "CodeSystem '${codeSystem.name}' does not have the valueSet parameter set, this is unsupported!"
            logger.error(error)
            return ValueSetExpansionOperation(
                canonicalUrl = codeSystem.valueSet ?: codeSystem.url,
                valueSetExpansion = null,
                conversionError = TerminologyConversionError(error)
            )
        }
        val concepts = codeSystem.concept.map {
            ValueSetExpansion.ValueSetConcept(
                system = codeSystem.url,
                code = it.code,
                display = it.display
            )
        }
        return ValueSetExpansionOperation(
            codeSystem.valueSet,
            ValueSetExpansion(
                canonicalUrl = codeSystem.valueSet,
                name = codeSystem.name,
                title = codeSystem.title,
                description = codeSystem.description,
                versionId = codeSystem.meta.versionId ?: "1",
                businessVersion = codeSystem.version,
                concepts = concepts
            )
        )
    }

    class MissingLinkException(message: String) : Exception(message)
    class MissingExpansionException(message: String) : Exception(message)
    class UnsupportedResourceTypeException(message: String) : Exception(message)
}