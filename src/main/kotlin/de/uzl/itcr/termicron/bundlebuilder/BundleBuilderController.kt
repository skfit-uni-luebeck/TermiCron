package de.uzl.itcr.termicron.bundlebuilder

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.ingest.FhirUtilities
import de.uzl.itcr.termicron.ingest.cleanUri
import org.apache.http.entity.ContentType
import org.hl7.fhir.r4.model.Bundle
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.net.URL
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.util.*

@Controller
@SessionAttributes("resources", "endpoints", "bundleString", "bundleId")
class BundleBuilderController(
    @Autowired val bundleBuilderDefaults: BundleBuilderDefaults,
    @Autowired val bundleBuilderService: BundleBuilderService,
    @Autowired val fhirContext: FhirContext,
    @Autowired val logger: Logger
) {

    @GetMapping("/")
    fun renderStartPage(
        model: Model,
    ): String {
        if (!model.containsAttribute("defaultServers")) {
            model["defaultServers"] = bundleBuilderDefaults.defaultServerMap
        }
        return "start"
    }

    @GetMapping("/query", "/build", "/list-resources", "/create-bundle")
    fun redirectInvalidGet() = "redirect:/"

    @PostMapping("/query")
    fun handleQueryRequest(
        model: Model,
        @RequestParam allRequestParam: Map<String, String>
    ): String {
        val endpoints =
            allRequestParam
                .filter { it.key.startsWith("endpoint-") }
                .filter { it.value.isNotBlank() }
                .values
        val validations = endpoints.associateWith { validateAnEndpoint(it) }
        return when (validations.values.contains(false)) {
            true -> {
                model["endpoints"] =
                    endpoints.mapIndexed { k, v -> BundleBuilderDefaults.DefaultServerEntry(k, v) }
                model["validationError"] = true
                renderStartPage(model)
            }
            false -> {
                logger.info("Using endpoints: ${endpoints.joinToString(", ")}")
                renderSelect(model, endpoints.toList())
            }
        }
    }

    @PostMapping("/build")
    fun renderBundle(
        model: Model,
        @SessionAttribute resources: List<BundleBuilderService.ReferenceList>,
        @RequestParam allParams: Map<String, String>
    ): String {
        val uuidRegex = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
        val selectedElements =
            allParams.filter { uuidRegex.matches(it.key) }.keys.toList()
        val bundleId = allParams.entries.first { it.key.lowercase(Locale.getDefault()) == "bundle-id" }.value
        val bundle = bundleBuilderService.buildBundle(resources, selectedElements, bundleId)
        model["bundleString"] = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle)
        model["bundleId"] = bundleId
        return "bundle_result"
    }

    fun renderSelect(
        model: Model,
        @SessionAttribute endpoints: List<String>
    ): String {
        model["endpoints"] = endpoints
        model["resources"] = bundleBuilderService.gatherCsVsForUrlList(endpoints)
        return "select_resources"
    }

    @PostMapping("create-bundle")
    fun createBundleOnServer(
        @RequestParam(name = "submit") endpoint: String,
        @SessionAttribute bundleString: String,
        @SessionAttribute bundleId: String
    ) {
        val utilities = FhirUtilities(StaticHelpers.httpClient(), fhirContext)
        val requestUri = URI.create("${endpoint.cleanUri()}Bundle/${bundleId}")
        val createRequest: HttpRequest = HttpRequest.newBuilder(requestUri)
            .PUT(HttpRequest.BodyPublishers.ofString(bundleString))
            .header("Content-Type", ContentType.APPLICATION_JSON.mimeType)
            .build()
        val resultBundle = utilities.executeFhirStatementParsing<Bundle>(createRequest)
        logger.info("Created bundle '$bundleId' at: $requestUri")
    }

    private fun validateAnEndpoint(endpointString: String): Boolean = try {
        URI.create(endpointString)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}


@ConstructorBinding
@ConfigurationProperties(prefix = "bundlebuilder.defaults")
class BundleBuilderDefaults(
    defaultServers: List<String> = listOf()
) {
    val defaultServerMap = defaultServers.mapIndexed { index, s ->
        DefaultServerEntry(index + 1, s)
    }

    data class DefaultServerEntry(
        val id: Int,
        val url: String
    )
}
