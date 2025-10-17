package io.github.kotlin.fibonacci

import io.github.kotlin.fibonacci.ui.UIManager
import javax.swing.SwingUtilities

/**
 * 主程序入口
 */
fun main() {
    SwingUtilities.invokeLater {
        val uiManager = UIManager()
        uiManager.createAndShowGUI()
    }
}