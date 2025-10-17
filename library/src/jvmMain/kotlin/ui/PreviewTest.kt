package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import javax.swing.*

/**
 * 预览系统测试
 */
fun main() {
    // 创建测试窗口
    val frame = JFrame("预览系统测试")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(800, 600)
    
    // 创建预览管理器
    val previewManager = EnhancedPreviewManager()
    
    // 创建测试表格
    val table = JTable()
    val scrollPane = JScrollPane(table)
    
    // 更新机台配置预览
    previewManager.updateMachineConfigPreview(table)
    
    frame.contentPane.add(scrollPane)
    frame.isVisible = true
    
    println("机台配置预览测试完成！")
    println("机台规则数量: ${previewManager.machineRules.size}")
    previewManager.machineRules.forEach { rule ->
        println("机台: ${rule.machineId}, 模具: ${rule.moldId}, 管规格: ${rule.pipeSpecs.size}个")
    }
}
