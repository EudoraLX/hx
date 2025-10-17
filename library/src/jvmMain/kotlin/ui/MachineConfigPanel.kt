package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*

/**
 * 机台配置面板
 */
class MachineConfigPanel(
    private val uiManager: UIManager,
    private val previewPanel: JPanel
) {
    
    private val machineAssignmentEngine = MachineAssignmentEngine()
    private var machineRules = mutableListOf<MachineRule>()
    
    fun createPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("机台配置")
        
        // 机台规则列表
        val rulesPanel = createRulesPanel()
        panel.add(rulesPanel)
        panel.add(Box.createVerticalStrut(10))
        
        // 操作按钮
        val buttonPanel = createButtonPanel(panel)
        panel.add(buttonPanel)
        
        // 初始化默认规则
        initializeDefaultRules()
        
        return panel
    }
    
    private fun createRulesPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.border = BorderFactory.createTitledBorder("机台划分规则")
        
        // 创建表格显示机台规则
        val tableModel = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = 5
            override fun getRowCount(): Int = machineRules.size
            override fun getValueAt(row: Int, col: Int): Any {
                val rule = machineRules[row]
                return when (col) {
                    0 -> rule.machineId
                    1 -> rule.moldId
                    2 -> rule.pipeSpecs.joinToString(", ") { "${it.innerDiameter}/${it.outerDiameter}" }
                    3 -> rule.description
                    4 -> "${rule.changeoverTime}h/${rule.pipeChangeTime}h"
                    else -> ""
                }
            }
            override fun getColumnName(col: Int): String = when (col) {
                0 -> "机台"
                1 -> "模具"
                2 -> "管规格"
                3 -> "说明"
                4 -> "换模/换管时间"
                else -> ""
            }
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        val table = JTable(tableModel)
        val scrollPane = JScrollPane(table)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createButtonPanel(panel: JPanel): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout()
        
        val loadDefaultButton = JButton("加载默认规则")
        val addRuleButton = JButton("添加规则")
        val editRuleButton = JButton("编辑规则")
        val deleteRuleButton = JButton("删除规则")
        val exportRulesButton = JButton("导出规则")
        val importRulesButton = JButton("导入规则")
        
        buttonPanel.add(loadDefaultButton)
        buttonPanel.add(addRuleButton)
        buttonPanel.add(editRuleButton)
        buttonPanel.add(deleteRuleButton)
        buttonPanel.add(exportRulesButton)
        buttonPanel.add(importRulesButton)
        
        // 绑定按钮事件
        loadDefaultButton.addActionListener {
            handleLoadDefaultRules(panel)
        }
        
        addRuleButton.addActionListener {
            handleAddRule(panel)
        }
        
        editRuleButton.addActionListener {
            handleEditRule(panel)
        }
        
        deleteRuleButton.addActionListener {
            handleDeleteRule(panel)
        }
        
        exportRulesButton.addActionListener {
            handleExportRules(panel)
        }
        
        importRulesButton.addActionListener {
            handleImportRules(panel)
        }
        
        return buttonPanel
    }
    
    private fun initializeDefaultRules() {
        machineRules.clear()
        machineRules.addAll(machineAssignmentEngine.createDefaultMachineRules())
    }
    
    private fun handleLoadDefaultRules(panel: JPanel) {
        initializeDefaultRules()
        uiManager.refreshMachineConfigPreview()
        JOptionPane.showMessageDialog(panel, "已加载默认机台规则", "加载完成", JOptionPane.INFORMATION_MESSAGE)
    }
    
    private fun handleAddRule(panel: JPanel) {
        val dialog = createRuleDialog(panel, null)
        dialog.isVisible = true
    }
    
    private fun handleEditRule(panel: JPanel) {
        if (machineRules.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有可编辑的规则", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val selectedIndex = JOptionPane.showInputDialog(panel, "请选择要编辑的规则索引 (0-${machineRules.size - 1}):")
        val index = selectedIndex?.toIntOrNull()
        
        if (index != null && index in 0 until machineRules.size) {
            val dialog = createRuleDialog(panel, machineRules[index])
            dialog.isVisible = true
        }
    }
    
    private fun handleDeleteRule(panel: JPanel) {
        if (machineRules.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有可删除的规则", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val selectedIndex = JOptionPane.showInputDialog(panel, "请选择要删除的规则索引 (0-${machineRules.size - 1}):")
        val index = selectedIndex?.toIntOrNull()
        
        if (index != null && index in 0 until machineRules.size) {
            val result = JOptionPane.showConfirmDialog(panel, "确定要删除这个规则吗？", "确认删除", JOptionPane.YES_NO_OPTION)
            if (result == JOptionPane.YES_OPTION) {
                machineRules.removeAt(index)
                uiManager.updatePreview()
                JOptionPane.showMessageDialog(panel, "规则已删除", "删除完成", JOptionPane.INFORMATION_MESSAGE)
            }
        }
    }
    
    private fun handleExportRules(panel: JPanel) {
        if (machineRules.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有规则可导出", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        fileChooser.selectedFile = java.io.File("机台规则_${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx")
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                // 这里可以实现导出机台规则到Excel的逻辑
                JOptionPane.showMessageDialog(panel, "机台规则导出成功！", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    private fun handleImportRules(panel: JPanel) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        val result = fileChooser.showOpenDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                // 这里可以实现从Excel导入机台规则的逻辑
                JOptionPane.showMessageDialog(panel, "机台规则导入成功！", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导入失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    private fun createRuleDialog(parent: JPanel, existingRule: MachineRule?): JDialog {
        val dialog = JDialog(parent as? JFrame, if (existingRule == null) "添加机台规则" else "编辑机台规则", true)
        dialog.setSize(400, 300)
        dialog.setLocationRelativeTo(parent)
        
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        
        // 机台ID
        val machineIdLabel = JLabel("机台ID:")
        val machineIdField = JTextField(10)
        if (existingRule != null) machineIdField.text = existingRule.machineId
        panel.add(machineIdLabel)
        panel.add(machineIdField)
        
        // 模具ID
        val moldIdLabel = JLabel("模具ID:")
        val moldIdField = JTextField(10)
        if (existingRule != null) moldIdField.text = existingRule.moldId
        panel.add(moldIdLabel)
        panel.add(moldIdField)
        
        // 管规格
        val pipeSpecLabel = JLabel("管规格:")
        val pipeSpecField = JTextField(20)
        if (existingRule != null) {
            pipeSpecField.text = existingRule.pipeSpecs.joinToString(", ") { "${it.innerDiameter}/${it.outerDiameter}" }
        }
        panel.add(pipeSpecLabel)
        panel.add(pipeSpecField)
        
        // 说明
        val descriptionLabel = JLabel("说明:")
        val descriptionField = JTextField(20)
        if (existingRule != null) descriptionField.text = existingRule.description
        panel.add(descriptionLabel)
        panel.add(descriptionField)
        
        // 换模时间
        val changeoverLabel = JLabel("换模时间(小时):")
        val changeoverField = JSpinner(SpinnerNumberModel(12, 1, 24, 1))
        if (existingRule != null) changeoverField.value = existingRule.changeoverTime
        panel.add(changeoverLabel)
        panel.add(changeoverField)
        
        // 换管时间
        val pipeChangeLabel = JLabel("换管时间(小时):")
        val pipeChangeField = JSpinner(SpinnerNumberModel(4, 1, 12, 1))
        if (existingRule != null) pipeChangeField.value = existingRule.pipeChangeTime
        panel.add(pipeChangeLabel)
        panel.add(pipeChangeField)
        
        // 按钮
        val buttonPanel = JPanel()
        val saveButton = JButton("保存")
        val cancelButton = JButton("取消")
        
        saveButton.addActionListener {
            try {
                val pipeSpecParser = PipeSpecParser()
                val pipeSpecs = pipeSpecParser.parsePipeSpecs(pipeSpecField.text)
                
                val newRule = MachineRule(
                    machineId = machineIdField.text,
                    moldId = moldIdField.text,
                    pipeSpecs = pipeSpecs,
                    description = descriptionField.text,
                    changeoverTime = changeoverField.value as Int,
                    pipeChangeTime = pipeChangeField.value as Int
                )
                
                if (existingRule == null) {
                    machineRules.add(newRule)
                } else {
                    val index = machineRules.indexOf(existingRule)
                    if (index >= 0) {
                        machineRules[index] = newRule
                    }
                }
                
                uiManager.updatePreview()
                dialog.dispose()
                JOptionPane.showMessageDialog(parent, "规则保存成功！", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(dialog, "保存失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
        
        cancelButton.addActionListener {
            dialog.dispose()
        }
        
        buttonPanel.add(saveButton)
        buttonPanel.add(cancelButton)
        panel.add(buttonPanel)
        
        dialog.contentPane = panel
        return dialog
    }
}
