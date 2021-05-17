package de.uzl.itcr.termicron.ingest.fhirdirectory

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import ca.uhn.fhir.parser.IParser
import de.uzl.itcr.termicron.ingest.*
import de.uzl.itcr.termicron.ingest.worker.ValueSetTsExpander
import org.hl7.fhir.r4.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileFilter
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant
import java.util.*
import kotlin.NoSuchElementException
import kotlin.UnsupportedOperationException

@Suppress("UsePropertyAccessSyntax")
class FhirDirectoryProvider(
    val directory: String,
    fhirContext: FhirContext,
    expansionSupportEndpoint: String? = null,
    private val expansionSupportHttpClientInitializer: (HttpClient.Builder.() -> Unit)? = null,
) : FhirIngestProvider(fhirContext) {

    private val expansionSupportEndpoint: URI? = expansionSupportEndpoint?.let { URI.create("${it.trimEnd('/')}/") }

    private val logger: Logger = LoggerFactory.getLogger(FhirDirectoryProvider::class.java)

    override fun retrieveResourcesToConvert(): Bundle {
        val filteredFiles = filterFhirResources(getResourceFilenamesFromDirectory())

        return filteredFiles.find { it.resourceType == ValueSetIngestPipeline.FhirResourceTypes.BUNDLE }.let {
            when (it) {
                null -> buildBundleFromResources(filteredFiles)
                else -> it.parseTo(fhirContext) as Bundle
            }
        }
    }

    override fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource {
        val bundleComponent = bundle.entry.find { it.getLink(relation.relationName).url == url }
            ?: throw NoSuchElementException("no resource with ${relation.relationName} = $url could be found in the bundle")
        return readResourceFromFile(
            File(
                bundleComponent.getResourceLink()
            )
        ).parseTo(fhirContext)
    }

    @Throws(UnsupportedOperationException::class, FhirServerError::class)
    override fun expandValueSet(valueSet: ValueSet): ValueSet = this.expansionSupportEndpoint?.let {
        ValueSetTsExpander(
            expansionSupportHttpClientInitializer,
            it.toString(),
            fhirContext
        ).expand(valueSet)
    }
        ?: throw UnsupportedOperationException("expansion using a terminology server is not supported for this instance")

    override fun supportsExpansion(): Boolean = this.expansionSupportEndpoint != null

    fun buildDefaultBundle(): Bundle =
        this.buildBundleFromResources(this.filterFhirResources(this.getResourceFilenamesFromDirectory()))

    fun buildBundleFromResources(resourceFiles: List<FhirResourceOnDisk>): Bundle {
        val onlyCodeSystemValueSet =
            resourceFiles.filter {
                it.resourceType == ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM ||
                        it.resourceType == ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET
            }
        val bundle = Bundle()
        bundle.apply {
            type = Bundle.BundleType.COLLECTION
            timestamp = Date.from(Instant.now())
            onlyCodeSystemValueSet.forEach { r ->
                this.addEntry().apply {
                    this.addLink()
                        .setRelation(ValueSetIngestPipeline.BundleLinkRelations.CANONICAL.relationName)
                        .setUrl(r.getCanonicalUriOfResource(fhirContext))
                    this.addLink()
                        .setRelation(r.resourceType.resourceName)
                        .setUrl(r.file.absolutePath)
                    this.setResource(r.parseTo(fhirContext))
                    /*this.setResource(
                        when (r.resourceType) {
                            ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET -> ValueSet()
                            ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM -> CodeSystem()
                            else -> throw FileTypeNotSupportedError("resource is of type ${r.resourceType}, not supported in the bundle")
                        }.apply {
                            url = r.getCanonicalUriOfResource(fhirContext)
                            version = r.getVersion(fhirContext)
                        }
                    )*/
                }
            }
        }
        return bundle
    }

    /**
     * get the filenames in the directory that could potentially represent FHIR resources.
     * This function does (currently) not recurse!
     * @return List<File> the list of file references, filtered using JSON and XML extensions
     */
    fun getResourceFilenamesFromDirectory(): List<File> {
        logger.info("retrieving files from ${File(directory).absolutePath}")
        return File(directory).listFiles(FileFilter {
            when (it.extension.toLowerCase()) {
                "xml", "json" -> true
                else -> false
            }
        })?.filterNotNull()
            ?: throw IllegalArgumentException("no readable files with extensions 'json' or 'xml' could be found in $directory")
    }

    /**
     * read the resource to a reference instance, so it can be read on-demand
     * @param file File the file reference
     * @return FhirResourceOnDisk the
     * @throws DataFormatException
     */
    @Throws(DataFormatException::class)
    fun readResourceFromFile(file: File): FhirResourceOnDisk {
        return FhirResourceOnDisk.getParserForFile(file, fhirContext).parseResource(file.reader())?.let {
            val resourceType: ValueSetIngestPipeline.FhirResourceTypes = when (it.fhirType()) {
                "CodeSystem" -> ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM
                "ValueSet" -> ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET
                "Bundle" -> ValueSetIngestPipeline.FhirResourceTypes.BUNDLE
                else -> ValueSetIngestPipeline.FhirResourceTypes.OTHER_FHIR_RESOURCE
            }
            FhirResourceOnDisk(file, resourceType, it.idElement.idPart)
        } ?: throw FileTypeNotSupportedError("HAPI was not able to parse the resource at ${file.absolutePath}")
    }

    /**
     * filter the list of files to a list of FHIR resources (exclude files that are not FHIR, just XML/JSON)
     * and parse them to ensure they are syntactically valid
     * @param files List<File> the list of files
     * @return List<FhirResourceOnDisk> the list of encapsulated resources
     */
    fun filterFhirResources(files: List<File>): List<FhirResourceOnDisk> =
        files.mapNotNull { file ->
            return@mapNotNull try {
                val resource = readResourceFromFile(file)
                when (resource.resourceType) {
                    ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM,
                    ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET,
                    ValueSetIngestPipeline.FhirResourceTypes.BUNDLE -> resource
                    else -> {
                        logger.debug("the resource ${file.name} is of FHIR type ${resource.resourceType}, which is not supported.")
                        null
                    }
                }
            } catch (e: DataFormatException) {
                logger.info("file ${file.absolutePath} is not parsable as FHIR")
                null
            }
        }

    /**
     * represent a file that encodes a FHIR resource
     * @property file File the file reference
     * @property resourceType String the resource type parameter of this resource
     * @property id String? the ID if appropriate
     */
    data class FhirResourceOnDisk(
        val file: File,
        val resourceType: ValueSetIngestPipeline.FhirResourceTypes,
        val id: String?
    ) {

        companion object {
            /**
             * get the parser for this file, XML or JSON, depending on extension (converted to lower case)
             * @param fhirContext FhirContext the fhir context to use
             * @return IParser the parser
             * @throws FileTypeNotSupportedError if the extension was not recognized
             */
            @Throws(FileTypeNotSupportedError::class)
            fun getParserForFile(file: File, fhirContext: FhirContext): IParser =
                when (file.extension.toLowerCase()) {
                    "xml" -> fhirContext.newXmlParser()
                    "json" -> fhirContext.newJsonParser()
                    else -> throw FileTypeNotSupportedError("${file.extension} is not supported for FHIR parsing")
                }
        }

        /**
         * determine the HAPI model class for this resource
         * @return Class<out Resource> the class this resource should be parsed to
         */
        private fun classForResourceType(): Class<out Resource> =
            when (resourceType) {
                ValueSetIngestPipeline.FhirResourceTypes.BUNDLE -> Bundle::class.java
                ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM -> CodeSystem::class.java
                ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET -> ValueSet::class.java
                else -> throw FileTypeNotSupportedError("unsupported file type")
            }

        /**
         * parse the resource represented by this instance to the corresponding resource type
         * @param fhirContext FhirContext the fhir context to use for parsing
         * @return Resource the parsed resource
         * @throws FileTypeNotSupportedError if the file could not be parsed appropriately
         */
        @Throws(FileTypeNotSupportedError::class)
        fun parseTo(fhirContext: FhirContext): Resource =
            getParserForFile(file, fhirContext).parseResource(classForResourceType(), file.reader())

        fun getCanonicalUriOfResource(fhirContext: FhirContext): String =
            when (resourceType) {
                ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM -> parseToCodeSystem(fhirContext).url
                ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET -> parseToValueSet(fhirContext).url
                else -> throw FileTypeNotSupportedError("neither a code system nor a value set - has no canonical uri")
            }

        /*/**
         * get the canonical URI of the valueset. for a vs instance, this is identical to the url, while for CS it is given by the
         * valueSet parameter - this has to be present for CodeSystems!
         * @param fhirContext FhirContext the fhir context to use
         * @return String the canonical url of the valueset
         * @throws ValueSetIngestPipeline.MissingValueSetUrlException if the implicit VS url is missing in a codeSystem
         * @throws FileTypeNotSupportedError
         */
        @Throws(ValueSetIngestPipeline.MissingValueSetUrlException::class, FileTypeNotSupportedError::class)
        fun getCanonicalUriOfValueSet(fhirContext: FhirContext): String =
            when (resourceType) {
                ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM -> parseToCodeSystem(fhirContext).valueSet
                    ?: throw ValueSetIngestPipeline.MissingValueSetUrlException("This CodeSystem does not have an implicit ValueSet defined - unsupported for synchronization!")
                ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET -> parseToValueSet(fhirContext).url
                else -> throw FileTypeNotSupportedError("neither a code system nor a value set - has no canonical uri")
            }*/

        /**
         * parse the resource to a code system
         * @param fhirContext FhirContext the fhir context
         * @return CodeSystem the parsed code system
         * @throws ClassCastException if the file could not be parsed appropriately
         */
        @Throws(ClassCastException::class)
        fun parseToCodeSystem(fhirContext: FhirContext) = parseTo(fhirContext) as CodeSystem

        /**
         * parse the resource to a value set
         * @param fhirContext FhirContext the fhir context
         * @return CodeSystem the parsed value set
         * @throws ClassCastException if the file could not be parsed appropriately
         */
        @Throws(ClassCastException::class)
        fun parseToValueSet(fhirContext: FhirContext) = parseTo(fhirContext) as ValueSet

        /**
         * parse the resource to a bundle
         * @param fhirContext FhirContext the fhir context
         * @return CodeSystem the parsed bundle
         * @throws ClassCastException if the file could not be parsed appropriately
         */
        @Throws(ClassCastException::class)
        fun parseToBundle(fhirContext: FhirContext) = parseTo(fhirContext) as Bundle

        /*fun getVersion(fhirContext: FhirContext): String = when (this.resourceType) {
            ValueSetIngestPipeline.FhirResourceTypes.CODE_SYSTEM -> parseToCodeSystem(fhirContext).version
            ValueSetIngestPipeline.FhirResourceTypes.VALUE_SET -> parseToValueSet(fhirContext).version
            else -> throw FileTypeNotSupportedError("neither a CS nor a VS, cannot retrieve version")
        }*/
    }
}