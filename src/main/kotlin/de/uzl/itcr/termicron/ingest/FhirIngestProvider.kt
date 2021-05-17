package de.uzl.itcr.termicron.ingest

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.r4.model.*

/**
 * an abstract FHIR ingest provider, that provided FHIR Terminology resources via a bundle
 *
 * @property fhirContext the HAPI FHIR context, instantiated somewhere above in the call hierarchy
 */
abstract class FhirIngestProvider(val fhirContext: FhirContext) {

    /**
     * get the bundle of resources to convert
     *
     * @return the bundle of resources
     */
    abstract fun retrieveResourcesToConvert(): Bundle?

    /**
     * retrieve a resource using a link and link relation. This may be a somewhat expensive operation.
     *
     * @param url the URL of the resource
     * @param relation the relation of the resource
     * @param bundle the bundle the resource is from
     * @return the resource via the URL
     */
    abstract fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource

    /**
     * retrieve a resource via a canonical URL
     *
     * @param canonical the canonical URL
     * @param bundle the bundle that contains the resource
     * @return the FHIR resource
     */
    fun retrieveResourceByResourceCanonical(canonical: String, bundle: Bundle): Resource =
        getResourceByUrlAndLinkRelation(canonical, ValueSetIngestPipeline.BundleLinkRelations.CANONICAL, bundle)

    /**
     * check if the FHIR ingest provider supports expansion of FHIR VS
     *
     * @return true if expansion is supported by the implementation and configuration
     */
    abstract fun supportsExpansion(): Boolean

    /**
     * expand the provided FHIR VS
     *
     * @param valueSet the VS
     * @return the expanded FHIR VS
     */
    abstract fun expandValueSet(valueSet: ValueSet): ValueSet

    /**
     * implementing FHIR ingest providers can override this fun to clean up temporary artefacts.
     * This fun is open and empty, so that:
     * - implementing classes do not need to provide an implementation unless required
     * - nothing is removed unless specified
     */
    open fun cleanupTemporaryArtifacts() {
        //no-op
    }

    companion object {
        /**
         * check if the provided bundle satisfies the requirements of TermiCron
         *
         * @param bundle the bundle to verify
         * @return a list of issues to display to the user. Null if the bundle is valid.
         */
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

/**
 * get the canonical URI of this BundleEntryComponent, from the links
 *
 * @return the canonical URI
 */
fun Bundle.BundleEntryComponent.getCanonicalLink(): String =
    this.getLink(ValueSetIngestPipeline.BundleLinkRelations.CANONICAL.relationName)?.url
        ?: throw MissingBundleLinkRelation("no canonical link for on this bundle component $this")

/**
 * get the resource URI of this BundleEntryComponent, from the links
 *
 * @return the resource URI
 */
fun Bundle.BundleEntryComponent.getResourceLink(): String = this.link.map { it.relation }.let { links ->
    return@let when {
        links.contains("CodeSystem") -> this.getLink("CodeSystem").url
        links.contains("ValueSet") -> this.getLink("ValueSet").url
        else -> throw MissingBundleLinkRelation("no relation CodeSystem/ValueSet in bundle entry")
    }
}

/**
 * exception that signifies a BundleEntryComponent does not have the required links
 *
 * @constructor
 * instantiates an Exception
 *
 * @param message the explanatory message
 */
class MissingBundleLinkRelation(message: String) : Exception(message)

/**
 * exception that signifies a FileType that is not supported
 *
 * @constructor
 * instantiates an Exception
 *
 * @param message the explanatory message
 */
class FileTypeNotSupportedError(message: String) : Exception(message)

/**
 * exception that signifies a bundle is not valid for use with TermiCron
 *
 * @constructor
 * instantiates an Exception, using the provided list of errors
 *
 * @param errors the list of errors
 */
class BundleValidationError(errors: List<String>) : Exception(errors.joinToString("; "))