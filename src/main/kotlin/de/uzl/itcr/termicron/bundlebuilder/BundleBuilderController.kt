package de.uzl.itcr.termicron.bundlebuilder

import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.random.Random

@Controller
@RequestMapping("/")
class BundleBuilderController(
    @Autowired val bundleBuilderDefaults: BundleBuilderDefaults
) {
    @GetMapping
    fun renderStartPage(
        model: Model
    ): String {
        model["defaultServers"] = bundleBuilderDefaults.defaultServers
        return "bundlebuilderstart"
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "bundlebuilder.defaults")
data class BundleBuilderDefaults(
    val defaultServers: List<String> = listOf()
)