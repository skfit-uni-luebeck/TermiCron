package de.uzl.itcr.termicron

import ca.uhn.fhir.context.FhirContext
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.sources.PropertiesValueSource
import de.uzl.itcr.termicron.authentication.cxxmdrauth.CxxMdrAuthenticationDriver
import de.uzl.itcr.termicron.authentication.cxxmdrauth.CxxMdrAuthConfiguration
import de.uzl.itcr.termicron.authentication.oauth.OAuthAuthenticationDriver
import de.uzl.itcr.termicron.authentication.oauth.OAuthDriverConfiguration
import de.uzl.itcr.termicron.configuration.ConversionPipeline
import de.uzl.itcr.termicron.configuration.CxxMdrConfiguration
import de.uzl.itcr.termicron.configuration.QL4MDRConfiguration
import de.uzl.itcr.termicron.ingest.ValueSetIngestPipeline
import de.uzl.itcr.termicron.ingest.fhirdirectory.FhirDirectoryProvider
import de.uzl.itcr.termicron.ingest.fhirserver.FhirServerProvider
import de.uzl.itcr.termicron.ingest.simplifier.SimplifierIngestProvider
import de.uzl.itcr.termicron.ingest.snomedctecl.EclQuery
import de.uzl.itcr.termicron.ingest.snomedctecl.SnomedCtEclIngestProvider
import de.uzl.itcr.termicron.output.MdrOutput
import de.uzl.itcr.termicron.output.cxx.CxxMdrOutputFile
import de.uzl.itcr.termicron.output.cxx.CxxMdrOutputRest
import de.uzl.itcr.termicron.output.ql4mdr.QL4MDRMdrOutput
import de.uzl.itcr.termicron.output.samply.SamplyMdrOutput
import de.uzl.itcr.termicron.synchronization.SynchronizationPipeline
import de.uzl.itcr.termicron.synchronization.cxx.CxxMdrSynchronization
import de.uzl.itcr.termicron.synchronization.files.FileMdrSynchronization
import de.uzl.itcr.termicron.synchronization.ql4mdr.QL4MDRSynchronization
import org.slf4j.Logger
import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * Clikt application for TermiCron
 *
 * @property fhirContext the HAPI FHIR context
 * @property log the logger of the application
 */
class TermiCronConsoleApplication(val fhirContext: FhirContext, private val log: Logger) : CliktCommand(
    printHelpOnEmptyArgs = true
) {

    /**
     * the conversion pipeline instance
     */
    private val pipeline by findOrSetObject { ConversionPipeline() }

    /**
     * we provide the Ingest context as the only subcommand,
     * and we make sure that (required) is shown for all required arguments
     */
    init {
        subcommands(Ingest(fhirContext, log))
        context {
            helpFormatter = CliktHelpFormatter(showRequiredTag = true)
        }
    }

    /**
     * called when the Clikt app is run
     */
    override fun run() {
        log.info("configuring pipeline")
        log.info(pipeline.toString())
    }

    /**
     * entry point for ingest configuration.
     * Customizers: add a new subcommand, and child class for the required console arguments
     *
     * @property fhirContext the HAPI FHIR context
     * @constructor
     * Ingest itself is no-op (has no run method), and calls its subcommands
     *
     * @param log the logger
     */
    class Ingest(val fhirContext: FhirContext, log: Logger) : NoOpCliktCommand(
        printHelpOnEmptyArgs = true,
    ) {
        init {
            subcommands(
                Directory(fhirContext, log),
                FhirServer(fhirContext, log),
                Simplifier(fhirContext, log),
                SnomedCtEcl(fhirContext, log)
            )
        }

        /**
         * an abstract subcommand for Ingest
         *
         * @property log the logger
         */
        abstract class IngestCommand(val log: Logger) : CliktCommand(
            printHelpOnEmptyArgs = true
        ) {

            /**
             * all Ingest commands require Output next
             */
            init {
                subcommands(Output(log = log))
            }

            /**
             * Should temporary artefacts be cleaned up after converting? (does not apply to all input pipelines!)
             */
            protected val cleanup by option(
                "--cleanup",
                help = "Should temporary artefacts be cleaned up after converting? (does not apply to all input pipelines!)"
            ).flag(default = false)

            /**
             * the pipeline, from the mother Clikt context
             */
            protected val pipeline by requireObject<ConversionPipeline>()
        }

        /**
         * Ingest files from a directory
         *
         * @property fhirContext the HAPI FHIR context
         * @constructor
         * This is an IngestCommand
         *
         * @param log the logger
         */
        class Directory(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {

            /**
             * input directory for the ingest process
             */
            private val directory by option(
                "--dir",
                "--directory",
                "-d",
                help = "input directory for the ingest process"
            )
                .path(mustExist = true, mustBeReadable = true)
                .required()

            /**
             * endpoint for ValueSet conversions
             */
            private val expansionSupportEndpoint: URL? by option(
                "--expansion-support",
                "--expansion-endpoint",
                "-e",
                help = "endpoint for ValueSet conversions"
            ).convert { URL(it) }

            /**
             * configure the ingest
             */
            override fun run() {
                log.info("directory ingest from '$directory'")
                log.info("supporting endpoint: ${expansionSupportEndpoint ?: "none"}")
                val ingestProvider = FhirDirectoryProvider(
                    directory.toAbsolutePath().toString(),
                    fhirContext,
                    expansionSupportEndpoint.toString()
                ) {
                    //handle authentication etc. here
                }
                pipeline.ingestPipeline = ValueSetIngestPipeline(ingestProvider, cleanup)
                log.info(pipeline.toString())
            }

        }

        /**
         * ingest resources from a FHIR server
         *
         * @property fhirContext the HAPI FHIR context
         * @constructor
         * This is an IngestCommand
         *
         * @param log the logger
         */
        class FhirServer(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {
            private val endpoint: String by option("--endpoint", "-e", "--url", "-u", help = "URL for the FHIR server")
                .required()
                .validate { validateUrl(it) }
            private val bundleId: String
                    by option("--bundle", "-b", help = "the ID of the bundle to use")
                        .required()

            /**
             * configure the ingest
             */
            override fun run() {
                log.info("server ingest from $endpoint with bundle $bundleId")
                val ingestProvider = FhirServerProvider(endpoint, bundleId, fhirContext) {
                    //configure authentication etc. here
                }
                pipeline.ingestPipeline = ValueSetIngestPipeline(ingestProvider, cleanup)
                log.info(pipeline.toString())
            }
        }

        /**
         * ingest resources from SNOMED CT ECL expressions
         *
         * @property fhirContext the HAPI FHIR context
         * @constructor
         * This is an IngestCommand
         *
         * @param log the logger
         */
        class SnomedCtEcl(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {

            /**
             * The ECL Query String (prompt if empty)
             */
            private val eclQuery: String by option(
                "--ecl",
                "-q",
                "--query",
                help = "The ECL Query String. You may also leave this out to enter the query interactively (which may work better with special characters)"
            ).prompt("ECL Query String? ")

            /**
             * URL for the FHIR based terminology server
             */
            private val terminologyServerEndpoint: String by option(
                "--url",
                "-u",
                help = "URL for the FHIR based terminology server"
            )
                .required()
                .validate { validateUrl(it) }

            private val defaultSnomedEdition = "900000000000207008"

            /**
             * The SNOMED CT Edition/Focus Module ID identifier. Uses the International Edition by default. Reference: https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples
             */
            private val snomedCtEdition by option(
                "--sct-edition",
                "-e",
                help = "The SNOMED CT Edition/Focus Module ID identifier. Uses the International Edition ($defaultSnomedEdition) by default. Reference: https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples"
            ).default(defaultSnomedEdition)

            /**
             * The SNOMED CT version to use. If left out, the default version of the TS is used, which is not necessarily the latest version of the chosen edition.
             */
            private val snomedCtVersion: String? by option(
                "--sct-version", "-v",
                help = "The SNOMED CT version to use. If left out, the default version of the TS is used, which is not necessarily the latest version of the chosen edition."
            )

            /**
             * the version of the resulting ValueSet
             */
            private val valueSetVersion: String by option(
                "--vs-version", "-r",
                help = "the version of the resulting ValueSet, use SemVer or YYYYMMDD format if possible"
            ).required()

            /**
             * the (machine readable) name of the resulting ValueSet
             */
            private val valueSetName: String by option(
                "--vs-name", "-n",
                help = "the (machine readable) name of the resulting ValueSet"
            ).required()

            /**
             * the (human readable) title of the resulting ValueSet
             */
            private val valueSetTitle: String by option(
                "--vs-title", "-t",
                help = "the (human readable) title of the resulting ValueSet"
            ).required()

            /**
             * the directory where the expanded FHIR resource should be written to
             */
            private val fhirOutputDirectory: File? by option(
                "--fhir-output", "-o",
                help = "If needed, specify a directory where the FHIR ValueSet should be exported to in application/fhir+json format. " +
                        "The directory must exist already."
            ).file(mustExist = true, canBeDir = true, canBeFile = false)

            /**
             * configure the ingest
             */
            override fun run() {
                log.info("ECL ingest from $terminologyServerEndpoint with query '${eclQuery}'")
                val query = EclQuery(
                    ecl = eclQuery,
                    terminologyServerEndpoint = terminologyServerEndpoint,
                    snomedCtEdition = snomedCtEdition,
                    snomedCtVersion = snomedCtVersion,
                    valueSetName = valueSetName,
                    valueSetTitle = valueSetTitle,
                    valueSetVersion = valueSetVersion,
                    outputDirectory = fhirOutputDirectory,
                    fhirContext = fhirContext
                )
                val provider = SnomedCtEclIngestProvider(query, fhirContext)
                pipeline.ingestPipeline = ValueSetIngestPipeline(provider, false)
            }
        }

        /**
         * ingest resources from a Simplifier
         *
         * @property fhirContext the HAPI FHIR context
         * @constructor
         * This is an IngestCommand
         *
         * @param log the logger
         */
        class Simplifier(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {

            /**
             * name of the package to download
             */
            private val packageName by option(
                "--package-name",
                "--package",
                "-p",
                help = "name of the package to download"
            ).required()

            /**
             * version of the package to download
             */
            private val packageVersion by option(
                "--package-version",
                "--version",
                "-v",
                help = "version of the package to download. Leave empty to use the most recent version"
            )

            /**
             * Endpoint for the (Simplifier) package API
             */
            private val simplifierUrl by option(
                "--simplifier-packages-url",
                help = "Endpoint for the Simplifier package API, default: ${SimplifierIngestProvider.simplifierDefaultUrl}"
            ).convert { URL(it) }
                .default(URL(SimplifierIngestProvider.simplifierDefaultUrl))

            /**
             * endpoint for ValueSet conversions
             */
            private val expansionSupportEndpoint: URL? by option(
                "--expansion-support",
                "--expansion-endpoint",
                "-e",
                help = "endpoint for ValueSet conversions"
            ).convert { URL(it) }

            /**
             * configure the ingest
             */
            override fun run() {
                log.info("Simplifier ingest of '$packageName@${if (packageVersion == null) "latest" else packageVersion}' from $simplifierUrl")
                log.info("supporting endpoint: ${expansionSupportEndpoint ?: "none"}")
                val provider = SimplifierIngestProvider(
                    packageName = packageName,
                    packageVersion = packageVersion,
                    fhirContext = fhirContext,
                    simplifierRegistry = simplifierUrl,
                    expansionSupportEndpoint = expansionSupportEndpoint.toString()
                ) {
                    //configure authentication etc. here
                }
                pipeline.ingestPipeline = ValueSetIngestPipeline(ingestProvider = provider, cleanup = cleanup)
                log.info(pipeline.toString())
            }

        }
    }

    /**
     * entry point for output configuration.
     * Customizers: add a new subcommand, and child class for the required console arguments
     *
     * @constructor
     * Output itself is no-op (has no run method), and simply calls its subcommands
     *
     * @param log the logger
     */
    class Output(val log: Logger) : NoOpCliktCommand() {
        init {
            subcommands(File(log), CxxRest(log), Ql4Mdr(log))
        }

        /**
         * output to files with the respective MDR format
         *
         * @property log the logger
         */
        class File(private val log: Logger) : FileOutputProvider(
            "Create file for manual upload to an MDR"
        ) {

            /**
             * Using an enum here allows for concise and exhaustive `when` statements to make sure every MDR available is configured
             * @property displayName String what is shown for the user in the help text. When they enter the value, the case is not required to match
             */
            enum class MdrChoices(val displayName: String) {
                CXX("CXX"),
                CENTRAXX("CentraXX"),
                SAMPLY("Samply"),
                CXXREST("CXX-REST"),
                CENTRAXXREST("CentraXX-REST"),
                QL4MDR("QL4MDR")
            }

            /**
             * the display names for the MDR choices are used for the Clikt configuration
             */
            private val mdrArgs = MdrChoices.values().map { it.displayName }

            /**
             * convert the MDR display name to a member of the MdrChoices enum, ignoring case
             * @param displayName String the display name as provided by the user
             * @return MdrChoices the matching MdrChoices member, or throws.
             * @throws NoSuchElementException if no matching element could be found in the enum (which should not happen if this function is invoked via Clikt's `.convert {}` !)
             */
            @Throws(NoSuchElementException::class)
            private fun cxxDisplayNameToMdrChoice(displayName: String): MdrChoices =
                MdrChoices.values().associateBy { it.displayName.toLowerCase() }.let { c ->
                    c[displayName.toLowerCase()]
                        ?: throw NoSuchElementException("no MDR $displayName is configured")
                }

            /**
             * choose the MDR output format from the available choices
             */
            private val mdr by option(
                "--mdr",
                "-m",
                "--format",
                "-f",
                help = "Specify the MDR to output data for; choices: [${mdrArgs.joinToString(", ")}]"
            )
                .choice(ignoreCase = true, choices = mdrArgs.toTypedArray())
                .convert { cxxDisplayNameToMdrChoice(it) }
                .required()

            /**
             * the catalog type for CentraXX, if exporting to CentraXX MDR
             */
            private val catalogTypeCXX by option(
                "--catalog-type",
                help = "the catalog type argument, if exporting to CentraXX"
            )
                .default("FHIR")

            /**
             * configure the output, and start the pipeline
             */
            override fun run() {
                log.info("Writing ${mdr.name} format to $outputPath")
                val mdrOutput: MdrOutput = when (mdr) {
                    MdrChoices.CXX, MdrChoices.CENTRAXX -> CxxMdrOutputFile(catalogTypeCXX)
                    MdrChoices.CXXREST, MdrChoices.CENTRAXXREST -> CxxMdrOutputRest(catalogTypeCXX)
                    MdrChoices.SAMPLY -> SamplyMdrOutput()
                    MdrChoices.QL4MDR -> QL4MDRMdrOutput()
                }
                pipeline.synchronizationPipeline =
                    SynchronizationPipeline(
                        FileMdrSynchronization(
                            mdrOutput,
                            outputPath.toFile()
                        )
                    )
                runPipeline(pipeline, log)
            }
        }

        /**
         * for outputting to a CentraXX MDR over REST
         *
         * @constructor
         * implements a WebOutputProvider
         *
         * @param log the log
         */
        class CxxRest(log: Logger) : WebOutputProvider(
            "Create/Update catalogs on a CentraXX MDR.\u0085You can provide arguments using the following command line arguments, " +
                    "using a properties file 'cxx-rest.properties' or using environment variables as required.",
            log
        ) {
            /**
             * URL for the authentication procedure
             */
            private val authEndpoint
                    by option(
                        "--auth",
                        "-a",
                        help = "URL for the authentication procedure, i.e. probably the server base"
                    )
                        .convert { URL(it) }
                        .required()

            /**
             * the client id for obtaining authentication tokens
             */
            private val clientId
                    by option(
                        "--client-id", "-i",
                        help = "the client id for obtaining authentication tokens"
                    )
                        .required()

            /**
             * the client secret for obtaining authentication token
             */
            private val clientSecret
                    by option(
                        "--client-secret", "-s",
                        help = "the client secret for obtaining authentication token"
                    )
                        .required()

            /**
             * the user name to authenticate as
             */
            private val userName
                    by option(
                        "--user", "--username", "--user-name", "-u",
                        help = "the user name to authenticate as"
                    )
                        .required()

            /**
             * the password of the same user
             */
            private val password
                    by option(
                        "--password", "-p",
                        help = "the password of the same user"
                    )
                        .required()

            /**
             * the catalog type argument
             */
            private val catalogTypeCentraxx by option(
                "--catalog-type",
                help = "the catalog type argument, default: $defaultCatalogType"
            )
                .default(defaultCatalogType)

            /**
             * initialize the Clikt context: options can be provided using environment variables,
             * or cxx-rest.properties by file
             *
             * @param builder the Clikt context builder
             */
            override fun initCliktContext(builder: Context.Builder) {
                builder.autoEnvvarPrefix = "CXX"
                builder.valueSource = PropertiesValueSource.from("cxx-rest.properties")
            }

            /**
             * the body of the run function, configuring the CXX pipeline
             */
            override fun runFunctionBody() {
                val cxxMdrConfiguration = CxxMdrConfiguration(
                    CxxMdrAuthConfiguration(
                        authEndpoint,
                        userName, password, clientId, clientSecret
                    ),
                    apiEndpoint
                )
                val authDriver = CxxMdrAuthenticationDriver(cxxMdrConfiguration.authenticationConfiguration)
                val outputDriver = CxxMdrOutputRest(catalogTypeCentraxx)
                val cxxMdrSynchronization = CxxMdrSynchronization(authDriver, cxxMdrConfiguration, outputDriver)
                val synchronizationPipeline = SynchronizationPipeline(cxxMdrSynchronization)
                log.info("Using CXX REST api.")
                pipeline.authenticationConfiguration = cxxMdrConfiguration.authenticationConfiguration
                pipeline.synchronizationPipeline = synchronizationPipeline
            }

            companion object {
                /**
                 * the default catalog type to use for converting
                 */
                const val defaultCatalogType = "FHIR"
            }
        }

        /**
         * Create concepts/concept systems using GraphQL
         *
         * @constructor
         * implements a WebOutputProvider
         *
         * @param log the application log
         */
        class Ql4Mdr(log: Logger) : WebOutputProvider(
            "Create concepts using GraphQL",
            log
        ) {

            /**
             * the OIDC url
             */
            private val openIdUrl by option("--oidc", "--auth-url", "-a")
                .convert { URL(it) }
                .prompt("OIDC base url?")

            /**
             * the OIDC clientID
             */
            private val clientId: String by option("--client-id", "-c").prompt("Client ID?")

            /**
             * the OIDC client secret
             */
            private val clientSecret: String by option("--client-secret", "-s").prompt("Client secret?")

            /**
             * the OIDC callback URL FQDN. A callback to http://fqdn:port/path must be allowed in the OIDC provider!
             */
            private val callbackDomain: String by option(
                "--callback-url",
                help = "Callback URL FQDN (without protocol, port and path!), default ${OAuthDriverConfiguration.defaultCallbackDomain}." +
                        "This must be enabled in your IdP!"
            ).default(OAuthDriverConfiguration.defaultCallbackDomain)

            /**
             * Port for callback URL
             */
            private val callbackPort: Int by option(
                "--callback-port",
                help = "Port for callback URL, default ${OAuthDriverConfiguration.defaultCallbackPort}"
            ).int()
                .default(OAuthDriverConfiguration.defaultCallbackPort)

            /**
             * Path for callback URL
             */
            private val callbackPath: String by option(
                "--callback-path",
                help = "Path for callback URL, default ${OAuthDriverConfiguration.defaultCallbackPath}"
            ).default(OAuthDriverConfiguration.defaultCallbackPath)

            /**
             * initialize the Clikt context so that settings can be provided with environment variables
             *
             * @param builder the Clikt context builder
             */
            override fun initCliktContext(builder: Context.Builder) {
                builder.autoEnvvarPrefix = "QL4MDR"
                builder.valueSource = PropertiesValueSource.from("ql4mdr.properties")
            }

            override fun runFunctionBody() {
                val oauthConfiguration = OAuthDriverConfiguration(
                    openIdUrl,
                    clientId,
                    clientSecret,
                    callbackDomain,
                    callbackPort,
                    callbackPath
                )
                pipeline.authenticationConfiguration = oauthConfiguration
                val authDriver = OAuthAuthenticationDriver(oauthConfiguration)
                val outputDriver = QL4MDRMdrOutput()
                val ql4mdrConfiguration = QL4MDRConfiguration(oauthConfiguration, apiEndpoint)
                val ql4MdrSynchronization = QL4MDRSynchronization(authDriver, ql4mdrConfiguration, outputDriver)
                val synchronizationPipeline = SynchronizationPipeline(ql4MdrSynchronization)
                log.info("Using QL4MDR API.")
                pipeline.authenticationConfiguration = ql4mdrConfiguration.authenticationConfiguration
                pipeline.synchronizationPipeline = synchronizationPipeline
            }
        }
    }

    /**
     * an abstract provider that outputs to the web
     *
     * @property log the log
     *
     * @constructor
     * instantiates a CliktCommand with the provided help string
     *
     * @param help the help string to pass to CliktContext (to show to the user)
     */
    abstract class WebOutputProvider(
        help: String,
        val log: Logger
    ) : CliktCommand(
        help = help,
        printHelpOnEmptyArgs = true
    ) {
        init {
            context {
                initCliktContext(this)
            }
        }

        /**
         * the pipeline from the mother context
         */
        protected val pipeline by requireObject<ConversionPipeline>()

        /**
         * the web endpoint to push resources to
         */
        val apiEndpoint by option("--endpoint", "-e", help = "URL for accessing the MDR API")
            .convert { URL(it) }
            .required()

        /**
         * the body to pass ot run, to configure the pipeline.
         * The pipeline is started after this function.
         */
        abstract fun runFunctionBody()

        /**
         * gets executed when the command parsing is complete
         * calls the runFunctionBody first, then the runPipeline.
         */
        override fun run() {
            runFunctionBody()
            runPipeline(pipeline, log)
        }

        /**
         * initialize the Clikt context for this subcommand
         * @param builder the context for this subcommand
         */
        abstract fun initCliktContext(builder: Context.Builder)
    }

    /**
     * output to files on disk, using the specified output provider
     *
     * @constructor
     * instantiates a CliktCommand with help
     *
     * @param help the help to show to the user
     */
    abstract class FileOutputProvider(help: String) : CliktCommand(
        help = help,
        printHelpOnEmptyArgs = true
    ) {
        /**
         * the pipeline from the mother context
         */
        protected val pipeline by requireObject<ConversionPipeline>()

        /**
         * the directory to write to
         */
        val outputPath by option("--output", "-o")
            .path(mustExist = false)
            .required()
    }
}

/**
 * validate that a String is a url, within this OptionTransformContext
 *
 * @param s the string to validate
 */
private fun OptionTransformContext.validateUrl(s: String) {
    try {
        URL(s)
    } catch (e: MalformedURLException) {
        fail("parameter provided is not a valid URL string: $s")
    }
}

/**
 * starts the pipeline, if it is configured
 *
 * @param pipeline the pipeline to run
 * @param log the log to use
 */
fun runPipeline(pipeline: ConversionPipeline, log: Logger) {
    if (pipeline.isConfigured()) {
        val outcomes = pipeline.runConversionPipeline()

        outcomes?.let { list ->
            log.info("Synchronization is complete. Here is the summary:")
            list.forEach {
                log.info("${it.first} -> ${it.second.name}")
            }
        } ?: log.error("An error occurred during conversion (see above for details)")
    } else {
        throw IllegalStateException("The pipeline was not configured end-to-end!")
    }
}