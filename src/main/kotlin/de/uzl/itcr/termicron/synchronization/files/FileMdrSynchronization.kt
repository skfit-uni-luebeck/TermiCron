package de.uzl.itcr.termicron.synchronization.files

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * implement output to files, via any output provider
 *
 * @property output the output provider
 * @property outputPath the path where files should be stored
 */
class FileMdrSynchronization(
    private val output: MdrOutput,
    private val outputPath: File,
) : MdrSynchronization() {

    /**
     * the logger for this class
     */
    private val log = LoggerFactory.getLogger(FileMdrSynchronization::class.java)

    /**
     * make sure that the output path exists
     */
    init {
        when {
            !outputPath.exists() -> if (!outputPath.mkdirs()) throw FileSystemException(
                outputPath,
                reason = "could not create the directory ${outputPath.absolutePath}"
            )
            outputPath.exists() && !outputPath.isDirectory -> throw FileSystemException(
                outputPath,
                reason = "file ${outputPath.absolutePath} exists, and is not a directory."
            )
        }
    }

    /**
     * get the filename for a ValueSet expansion relative to the output path.
     * This makes use of the provided MIME type.
     *
     * @param vs the VS to generate files for
     */
    private fun filename(vs: ValueSetExpansion) = output.mimeType().extensions
        .firstOrNull().let { ext ->
            outputPath
                .resolve("${vs.name}_${vs.businessVersionUrlEncoded}${ext ?: ".txt"}")
        }


    /**
     * check if the file of that name is present
     *
     * @param vs the VS
     * @return true if present
     */
    override fun isPresent(vs: ValueSetExpansion): Boolean =
        filename(vs).let { filename ->
            filename.exists().also {
                log.debug("file ${filename.absolutePath} already exists")
            }
        }

    /**
     * check if the file of that name is present and equal to the contents of the rendered catalog
     *
     * @param vs the VS
     * @return true if present and contents are identical
     */
    override fun isCurrent(vs: ValueSetExpansion): Boolean = try {
        filename(vs)
            .readText() == output.outputCatalog(vs).result
    } catch (e: IOException) {
        false
    }

    /**
     * create the file for vs
     *
     * @param vs the file to write out
     * @return true if successful
     */
    override fun create(vs: ValueSetExpansion): Boolean =
        try {
            filename(vs).let { filename ->
                filename.writeText(output.outputCatalog(vs).result ?: return false)
                log.info("File written at ${filename.absolutePath}")
            }
            true
        } catch (e: Exception) {
            false
        }

    /**
     * update == create
     *
     * @param vs the vs to update
     * @return true if successful
     */
    override fun update(vs: ValueSetExpansion): Boolean = create(vs)

    override fun validateEndpoint(): Boolean {
        return outputPath.canWrite().also { canWrite ->
            if (!canWrite) log.error("Can not write to specified output directory: ${outputPath.absolutePath}")
        }
    }
}