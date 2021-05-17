package de.uzl.itcr.termicron.synchronization.files

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.output.MdrOutput
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import org.slf4j.LoggerFactory
import java.io.File

class FileMdrSynchronization(
    private val output: MdrOutput,
    private val outputPath: File,
) : MdrSynchronization() {

    private val log = LoggerFactory.getLogger(FileMdrSynchronization::class.java)

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

    private fun filename(vs: ValueSetExpansion) = outputPath
        .resolve("${vs.name}_${vs.businessVersionUrlEncoded}${output.mimeType().extension}")

    override fun isPresent(vs: ValueSetExpansion): Boolean =
        filename(vs).let { filename ->
            filename.exists().also {
                log.debug("file ${filename.absolutePath} already exists")
            }
        }

    override fun isCurrent(vs: ValueSetExpansion): Boolean = filename(vs)
        .readText() == output.outputCatalog(vs).result

    override fun create(vs: ValueSetExpansion): Boolean {
        filename(vs).let { filename ->
            filename.writeText(output.outputCatalog(vs).result)
            log.info("File written at ${filename.absolutePath}")
        }
        return true
    }

    override fun update(vs: ValueSetExpansion): Boolean = create(vs)
}