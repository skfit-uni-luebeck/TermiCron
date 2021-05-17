package de.uzl.itcr.termicron.ingest

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.*

abstract class FhirIngestProvider(val fhirContext: FhirContext) {

    abstract fun retrieveResourcesToConvert(): Bundle?

    abstract fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource

    fun retrieveResourceByResourceCanonical(canonical: String, bundle: Bundle): Resource =
        getResourceByUrlAndLinkRelation(canonical, ValueSetIngestPipeline.BundleLinkRelations.CANONICAL, bundle)

    abstract fun supportsExpansion(): Boolean
    abstract fun expandValueSet(valueSet: ValueSet): ValueSet

    open fun cleanupTemporaryArtifacts() {
        //no-op
    }

    companion object {
        fun validateBundleSatisfiesRequirements(bundle: Bundle): List<String>? {
            val errors = mutableListOf<String>()
            when {
                bundle.hasType() -> {
                    if (bundle.type != Bundle.BundleType.COLLECTION) {
                        errors.add("bundle type must be Collection, got ${bundle.type}")
                    }
                }
                else -> errors.add("missing type for bundle")
            }
            if (!bundle.hasEntry()) errors.add("bundle must have entries")
            else {
                bundle.entry.forEachIndexed { index, it ->
                    if (!it.hasResource()) {
                        //NO-OP
                    } else if (it.resource.resourceType != ResourceType.CodeSystem && it.resource.resourceType != ResourceType.ValueSet)
                        errors.add("bundle entry at index $index is neither CodeSystem nor ValueSet - unsupported")
                    if (!it.hasLink())
                        errors.add("no links in the bundle element at index $index")
                }
            }
            return when (errors.count()) {
                0 -> null
                else -> errors
            }
        }
    }
}

fun Bundle.BundleEntryComponent.getCanonicalLink(): String =
    this.getLink(ValueSetIngestPipeline.BundleLinkRelations.CANONICAL.relationName)?.url
        ?: throw MissingBundleLinkRelation("no canonical link for on this bundle component $this")

fun Bundle.BundleEntryComponent.getResourceLink(): String = this.link.map { it.relation }.let { links ->
    return@let when {
        links.contains("CodeSystem") -> this.getLink("CodeSystem").url
        links.contains("ValueSet") -> this.getLink("ValueSet").url
        else -> throw MissingBundleLinkRelation("no relation CodeSystem/ValueSet in bundle entry")
    }
}


class MissingBundleLinkRelation(message: String) : Exception(message)

class FileTypeNotSupportedError(message: String) : Exception(message)

class BundleValidationError(errors: List<String>) : Exception(errors.joinToString("; "))