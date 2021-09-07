package de.uzl.itcr.termicron.bundlebuilder

import ca.uhn.fhir.context.FhirContext
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@Controller
@SessionAttributes("resources", "endpoints", "bundleString")
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
