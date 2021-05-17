package de.uzl.itcr.termicron.ingest.simplifier

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.ingest.FhirIngestProvider
import de.uzl.itcr.termicron.ingest.ValueSetIngestPipeline
import de.uzl.itcr.termicron.ingest.fhirdirectory.FhirDirectoryProvider
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ValueSet
import org.orienteer.jnpm.JNPMService
import org.orienteer.jnpm.JNPMSettings
import org.rauschig.jarchivelib.ArchiverFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path

class SimplifierIngestProvider(
    private val packageName: String,
    private var packageVersion: String? = null,
    fhirContext: FhirContext,
    val simplifierRegistry: URL = URL(simplifierDefaultUrl),
    expansionSupportEndpoint: String? = null,
    expansionSupportHttpClientInitializer: (HttpClient.Builder.() -> Unit)? = null
) : FhirIngestProvider(fhirContext) {

    private val packageDirectory = Files.createTempDirectory("${packageName}_$packageVersion")
    private val temporaryDirIngestProvider = FhirDirectoryProvider(
        packageDirectory.resolve("package").toString(),
        fhirContext,
        expansionSupportEndpoint,
        expansionSupportHttpClientInitializer
    )
    private val logger: Logger = LoggerFactory.getLogger(SimplifierIngestProvider::class.java)

    companion object {

        const val simplifierDefaultUrl = "https://packages.simplifier.net"

        private var jnpmInstance: JNPMService? = null
        val downloadDirectory: Path = Files.createTempDirectory("jnpm")
        private fun getJnpm(simplifierRegistry: URL): JNPMService {
            if (jnpmInstance == null) {
                JNPMService.configure(
                    JNPMSettings.builder()
                        .downloadDirectory(downloadDirectory)
                        .registryUrl(simplifierRegistry.toString())
                        .build()
                )
                jnpmInstance = JNPMService.instance()
            }
            return jnpmInstance!!
        }
    }

    fun downloadPackageSimplifier(): DownloadResult {
        val packageInfo =
            getJnpm(simplifierRegistry).let { it.getPackageInfo(packageName) ?: return DownloadResult.NO_SUCH_PACKAGE }
        packageVersion = when (packageVersion) {
            null -> packageInfo.latest
            else -> packageVersion
        }.also { latestVersion ->
            logger.info("Latest version of $packageName in Simplifier is: $latestVersion")
            if (packageVersion != null && packageVersion != latestVersion) {
                logger.warn("Latest version of $packageName is newer than specified version $latestVersion")
            }
        }
        val versionInfo = packageInfo.versions[packageVersion] ?: return DownloadResult.NO_SUCH_VERSION
        versionInfo.downloadTarball().blockingAwait()
        val archiver = ArchiverFactory.createArchiver("tar", "gz")
        try {
            archiver.extract(versionInfo.localTarball, packageDirectory.toFile())
        } catch (e: IOException) {
            logger.error("Error when extracting ${versionInfo.localTarball}", e)
            return DownloadResult.ERROR
        }
        return DownloadResult.SUCCESS
    }

    override fun retrieveResourcesToConvert(): Bundle {
        when (val result = downloadPackageSimplifier()) {
            DownloadResult.SUCCESS -> logger.info("Downloaded package $packageName@$packageVersion from simplifier")
            else -> {
                logger.error("Error when downloading package $packageName@$packageVersion from Simplifier: $result")
            }
        }
        return temporaryDirIngestProvider.retrieveResourcesToConvert()
    }

    override fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource = temporaryDirIngestProvider.getResourceByUrlAndLinkRelation(url, relation, bundle)

    override fun supportsExpansion(): Boolean = temporaryDirIngestProvider.supportsExpansion()

    override fun expandValueSet(valueSet: ValueSet): ValueSet = temporaryDirIngestProvider.expandValueSet(valueSet)

    override fun cleanupTemporaryArtifacts() {
        super.cleanupTemporaryArtifacts()
        deleteDirectory(downloadDirectory.toFile())
        deleteDirectory(packageDirectory.toFile())
    }

    private fun deleteDirectory(directoryToDelete: File) {
        logger.info("Deleting directory ${directoryToDelete.absolutePath}")
        if (!deleteDirectoryRecursively(directoryToDelete)) {
            logger.error("Error deleting ${directoryToDelete.absolutePath}")
        } else {
            logger.info("Successfully deleted ${directoryToDelete.absolutePath}")
        }
    }

    private fun deleteDirectoryRecursively(directoryToDelete: File): Boolean {
        val allFiles = directoryToDelete.listFiles()
        if (allFiles != null) {
            for (file in allFiles) {
                deleteDirectoryRecursively(file)
            }
        }
        return directoryToDelete.also {
            logger.debug("Deleting file ${it.absolutePath}")
        }.delete()

    }

    enum class DownloadResult {
        NO_SUCH_VERSION,
        NO_SUCH_PACKAGE,
        SUCCESS,
        ERROR
    }
}