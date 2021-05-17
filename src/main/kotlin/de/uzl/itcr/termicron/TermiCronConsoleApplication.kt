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

class TermiCronConsoleApplication(val fhirContext: FhirContext, private val log: Logger) : CliktCommand(
    printHelpOnEmptyArgs = true
) {

    private val pipeline by findOrSetObject { ConversionPipeline() }

    init {
        subcommands(Ingest(fhirContext, log))
        context {
            helpFormatter = CliktHelpFormatter(showRequiredTag = true)
        }
    }

    override fun run() {
        log.info("configuring pipeline")
        log.info(pipeline.toString())
    }

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

        class Directory(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {

            private val directory by option(
                "--dir",
                "--directory",
                "-d",
                help = "input directory for the ingest process"
            )
                .path(mustExist = true, mustBeReadable = true)
                .required()
            private val expansionSupportEndpoint: URL? by option(
                "--expansion-support",
                "--expansion-endpoint",
                "-e",
                help = "endpoint for ValueSet conversions"
            ).convert { URL(it) }

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

        class FhirServer(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {
            private val endpoint: String by option("--endpoint", "-e", "--url", "-u", help = "URL for the FHIR server")
                .required()
                .validate { validateUrl(it) }
            private val bundleId: String
                    by option("--bundle", "-b", help = "the ID of the bundle to use")
                        .required()

            override fun run() {
                log.info("server ingest from $endpoint with bundle $bundleId")
                val ingestProvider = FhirServerProvider(endpoint, bundleId, fhirContext) {
                    //configure authentication etc. here
                }
                pipeline.ingestPipeline = ValueSetIngestPipeline(ingestProvider, cleanup)
                log.info(pipeline.toString())
            }
        }

        class SnomedCtEcl(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {
            private val eclQuery: String by option(
                "--ecl",
                "-q",
                "--query",
                help = "The ECL Query String. You may also leave this out to enter the query interactively (which may work better with special characters)"
            ).prompt("ECL Query String? ")

            private val terminologyServerEndpoint: String by option(
                "--url",
                "-u",
                help = "URL for the FHIR based terminology server"
            )
                .required()
                .validate { validateUrl(it) }

            private val snomedCtEdition by option(
                "--sct-edition",
                "-e",
                help = "The SNOMED CT Edition/Focus Module ID identifier. Used the International Edition by default. Reference: https://confluence.ihtsdotools.org/display/DOCEXTPG/4.4.2+Edition+URI+Examples"
            ).default("900000000000207008")

            private val snomedCtVersion: String? by option(
                "--sct-version", "-v",
                help = "The SNOMED CT version to use. If left out, the default version of the TS is used, which is not necessarily the latest version of the chosen edition."
            )

            private val valueSetVersion: String by option(
                "--vs-version", "-r",
                help = "the version of the resulting ValueSet, use SemVer or YYYYMMDD format if possible"
            ).required()

            private val valueSetName: String by option(
                "--vs-name", "-n",
                help = "the (machine readable) name of the resulting ValueSet"
            ).required()

            private val valueSetTitle: String by option(
                "--vs-title", "-t",
                help = "the (human readable) title of the resulting ValueSet"
            ).required()

            private val fhirOutputDirectory: File? by option(
                "--fhir-output", "-o",
                help = "If needed, specify a directory where the FHIR ValueSet should be exported to in application/fhir+json format. " +
                        "The directory must exist already."
            ).file(mustExist = true, canBeDir = true, canBeFile = false)

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

        abstract class IngestCommand(val log: Logger) : CliktCommand(
            printHelpOnEmptyArgs = true
        ) {

            init {
                subcommands(Output(log = log))
            }

            protected val cleanup by option(
                "--cleanup",
                help = "Should temporary artefacts be cleaned up after converting? (does not apply to all input pipelines!)"
            ).flag(default = false)

            protected val pipeline by requireObject<ConversionPipeline>()
        }

        class Simplifier(val fhirContext: FhirContext, log: Logger) : IngestCommand(log) {

            private val packageName by option(
                "--package-name",
                "--package",
                "-p",
                help = "name of the package to download"
            ).required()
            private val packageVersion by option(
                "--package-version",
                "--version",
                "-v",
                help = "version of the package to download. Leave empty to use the most recent version"
            )
            private val simplifierUrl by option(
                "--simplifier-packages-url",
                help = "Endpoint for the Simplifier package API, default: ${SimplifierIngestProvider.simplifierDefaultUrl}"
            ).convert { URL(it) }
                .default(URL(SimplifierIngestProvider.simplifierDefaultUrl))
            private val expansionSupportEndpoint: URL? by option(
                "--expansion-support",
                "--expansion-endpoint",
                "-e",
                help = "endpoint for ValueSet conversions"
            ).convert { URL(it) }

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

    class Output(val log: Logger) : NoOpCliktCommand() {
        init {
            subcommands(File(log), CxxRest(log), SamplyGraphQl(log))
        }

        class File(private val log: Logger) : FileOutputProvider(
            "Create file for manual upload to an MDR"
        ) {

            /**
             * Using an enum here allows for concise `when` statements to make sure every MDR available is configured
             * @property displayName String what is shown for the user in the help text. When they enter the value, the case is not required to match
             */
            enum class MdrChoices(val displayName: String) {
                CXX("CXX"),
                CENTRAXX("CentraXX"),
                SAMPLY("Samply"),
                CXXREST("CXX-REST"),
                CENTRAXXREST("CentraXX-REST"),
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

            private val mdr by option(
                "--mdr",
                "-m",
                "--format",
                "-f",
                help = "Specify the MDR to output data for; choices: [${mdrArgs.joinToString(", ")}]"
            )
                .choice(ignoreCase = true, choices = mdrArgs.toTypedArray())
                //.convert { MdrChoices.valueOf(it.toUpperCase()) }
                .convert { cxxDisplayNameToMdrChoice(it) }
                .required()

            private val catalogTypeCXX by option(
                "--catalog-type",
                help = "the catalog type argument, if exporting to CentraXX"
            )
                .default("FHIR")

            override fun run() {
                log.info("Writing ${mdr.name} format to $outputPath")
                val mdrOutput: MdrOutput = when (mdr) {
                    MdrChoices.CXX, MdrChoices.CENTRAXX -> CxxMdrOutputFile(catalogTypeCXX)
                    MdrChoices.CXXREST, MdrChoices.CENTRAXXREST -> CxxMdrOutputRest(catalogTypeCXX)
                    MdrChoices.SAMPLY -> SamplyMdrOutput()
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

        class CxxRest(log: Logger) : WebOutputProvider(
            "Create/Update catalogs on a CentraXX MDR.\u0085You can provide arguments using the following command line arguments, " +
                    "using a properties file 'cxx-rest.properties' or using environment variables as required.",
            log
        ) {
            private val authEndpoint
                    by option(
                        "--auth",
                        "-a",
                        help = "URL for the authentication procedure, i.e. probably the server base"
                    )
                        .convert { URL(it) }
                        .required()
            private val clientId
                    by option("--client-id", "-i", help = "the client id for obtaining authentication tokens")
                        .required()
            private val clientSecret
                    by option("--client-secret", "-s", help = "the client secret for obtaining authentication token")
                        .required()
            private val userName
                    by option("--user", "--username", "--user-name", "-u", help = "the user name to authenticate as")
                        .required()
            private val password
                    by option("--password", "-p", help = "the password of the same user")
                        .required()

            private val catalogTypeCentraxx by option(
                "--catalog-type",
                help = "the catalog type argument, if exporting to CentraXX"
            )
                .default("FHIR")

            override fun initCliktContext(builder: Context.Builder) {
                builder.autoEnvvarPrefix = "CXX"
                builder.valueSource = PropertiesValueSource.from("cxx-rest.properties")
            }

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
        }

        class SamplyGraphQl(log: Logger) : WebOutputProvider(
            "Create concepts in Samply.MDR using GraphQL",
            log
        ) {

            private val openIdUrl by option("--oidc", "--auth-url", "-a")
                .convert { URL(it) }
                .prompt("OIDC base url?")

            private val clientId: String by option("--client-id", "-c").prompt("Client ID?")

            private val clientSecret: String by option("--client-secret", "-s").prompt("Client secret?")

            private val callbackDomain: String by option(
                "--callback-url",
                help = "Callback URL FQDN (without protocol and path!), default localhost. This must be enabled in your IdP!"
            ).default("localhost")

            private val callbackPort: Int by option("--callback-port", help = "Port for callback URL, default 8080").int()
                .default(8080)

            override fun initCliktContext(builder: Context.Builder) {
                builder.autoEnvvarPrefix = "Samply"
                builder.valueSource = PropertiesValueSource.from("samply-graphql.properties")
            }

            override fun runFunctionBody() {
                val oauthConfiguration = OAuthDriverConfiguration(
                    openIdUrl,
                    clientId,
                    clientSecret,
                    callbackDomain,
                    callbackPort
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

        protected val pipeline by requireObject<ConversionPipeline>()
        val apiEndpoint by option("--endpoint", "-e", help = "URL for accessing the MDR API")
            .convert { URL(it) }
            .required()

        abstract fun runFunctionBody()

        override fun run() {
            runFunctionBody()
            runPipeline(pipeline, log)
        }

        abstract fun initCliktContext(builder: Context.Builder)
    }

    abstract class FileOutputProvider(help: String) : CliktCommand(
        help = help,
        printHelpOnEmptyArgs = true
    ) {
        protected val pipeline by requireObject<ConversionPipeline>()
        val outputPath by option("--output", "-o")
            .path(mustExist = false)
            .required()
    }
}

private fun OptionTransformContext.validateUrl(s: String) {
    try {
        URL(s)
    } catch (e: MalformedURLException) {
        fail("parameter provided is not a valid URL string: $s")
    }
}

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