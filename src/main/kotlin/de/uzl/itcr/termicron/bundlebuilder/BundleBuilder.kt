package de.uzl.itcr.termicron.bundlebuilder

import com.googlecode.lanterna.TextCharacter
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.slf4j.Logger
import kotlin.random.Random

class BundleBuilder(private val fhirServerList: List<String>, val log: Logger) {

    private val terminal = DefaultTerminalFactory().createTerminal()
    private val screen = TerminalScreen(terminal).apply {
        startScreen()
    }/*
    private val textGui = MultiWindowTextGUI(screen);
    private val window = BasicWindow("TermiCron Bundle Builder")*/

    fun runBundleBuilder() {
        println("terminal ${terminal.terminalSize.columns} x ${terminal.terminalSize.rows}")
        for (column in 0..terminal.terminalSize.columns) {
            for (row in 0..terminal.terminalSize.rows) {
                val bgColor: TextColor.ANSI = TextColor.ANSI.values().random()
                screen.setCharacter(
                    column, row, TextCharacter.fromCharacter(
                        ' ', TextColor.ANSI.DEFAULT,
                        bgColor
                    ).first()
                )
            }
        }
        screen.refresh()
        Thread.yield()
        Thread.sleep(1000)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 1000 * 10) {
            if (screen.pollInput() != null)
                break
            Thread.sleep(1)
        }
    }
}