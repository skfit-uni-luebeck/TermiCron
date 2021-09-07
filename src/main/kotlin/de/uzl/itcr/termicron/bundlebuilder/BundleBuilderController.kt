package de.uzl.itcr.termicron.bundlebuilder

import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URI

@Controller
class BundleBuilderController(
    @Autowired val bundleBuilderDefaults: BundleBuilderDefaults,
    @Autowired val logger: Logger
) {

    @GetMapping("/")
    fun renderStartPage(
        model: Model,
    ): String {
        if (!model.containsAttribute("defaultServers")) {
            model["defaultServers"] = bundleBuilderDefaults.defaultServerMap
        }
        return "bundlebuilder_start"
    }

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
                model["defaultEndpoints"] =
                    endpoints.mapIndexed { k, v -> BundleBuilderDefaults.DefaultServerEntry(k, v) }
                model["validationError"] = true
                renderStartPage(model)
            }
            false -> {
                logger.info("Using endpoints: ${endpoints.joinToString(", ")}")
                renderSelect(endpoints.toList())
            }

        }
    }

    @PostMapping("/select-resources")
    fun renderSelect(
        endpoints: List<String>
    ): String {
        return "bundlebuilder_select_resources"
    }

    private fun validateAnEndpoint(endpointString: String): Boolean = try {
        URI.create(endpointString)
        true
    } catch (e: IllegalArgumentException) {
        false
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

}


/*

@Controller
@RequestMapping("/")
class BundleBuilderController(
    @Autowired val bundleBuilderDefaults: BundleBuilderDefaults,
    @Autowired val logger: Logger
) {

    @GetMapping("/")
    fun redirectRoot() = "redirect:/bundlebuilder"

    @PostMapping("/bundlebuilder/query")
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
            true, false -> {
                val defaultEndpoints = endpoints.mapIndexed { k, v -> BundleBuilderDefaults.DefaultServerEntry(k, v) }
                model["validationError"] = true
                renderStartPage(model, defaultEndpoints)
            }
            */
/*false -> {
                logger.info("Using endpoints: ${endpoints.joinToString(", ")}")
                renderSelect()
            }*//*

        }
    }

    @PostMapping("/bundlebuilder/select")
    fun renderSelect(
    ): String {
        return "bundlebuilder_select_resources"
    }

    private fun validateAnEndpoint(endpointString: String): Boolean = try {
        URI.create(endpointString)
        true
    } catch (e: IllegalArgumentException) {
        false
    }


    @GetMapping("/bundlebuilder")
    fun renderStartPage(
        model: Model,
        defaultServers: List<BundleBuilderDefaults.DefaultServerEntry> = bundleBuilderDefaults.defaultServerMap
    ): String {
        model["defaultServers"] = defaultServers
        return "bundlebuilder_start"
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
}*/
