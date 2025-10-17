package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*
import java.io.File

/**
 * 订单移除面板
 */
class OrderRemovalPanel(
    private val uiManager: UIManager,
    private val previewPanel: JPanel
) {
    // UI控件引用
    private var targetColumnCombo: JComboBox<String>? = null
    private var conditionCombo: JComboBox<String>? = null
    private var valueField: JTextField? = null
    
    fun createPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("已完成订单移除")
        
        // 比较列设置
        val columnGroup = createColumnGroup()
        panel.add(columnGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 移除选项
        val removalGroup = createRemovalGroup()
        panel.add(removalGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 操作按钮
        val buttonPanel = createButtonPanel(panel)
        panel.add(buttonPanel)
        
        return panel
    }
    
    private fun createColumnGroup(): JPanel {
        val columnGroup = JPanel()
        columnGroup.layout = BoxLayout(columnGroup, BoxLayout.Y_AXIS)
        columnGroup.border = BorderFactory.createTitledBorder("比较列设置")
        
        val plannedColumnLabel = JLabel("计划发货数量列:")
        val plannedColumnCombo = JComboBox<String>()
        plannedColumnCombo.preferredSize = Dimension(200, 25)
        
        val shippedColumnLabel = JLabel("已发货数列:")
        val shippedColumnCombo = JComboBox<String>()
        shippedColumnCombo.preferredSize = Dimension(200, 25)
        
        columnGroup.add(plannedColumnLabel)
        columnGroup.add(plannedColumnCombo)
        columnGroup.add(Box.createVerticalStrut(5))
        columnGroup.add(shippedColumnLabel)
        columnGroup.add(shippedColumnCombo)
        
        // 绑定列选择器更新事件
        bindColumnComboBoxes(plannedColumnCombo, shippedColumnCombo)
        
        return columnGroup
    }
    
    private fun createRemovalGroup(): JPanel {
        val removalGroup = JPanel()
        removalGroup.layout = BoxLayout(removalGroup, BoxLayout.Y_AXIS)
        removalGroup.border = BorderFactory.createTitledBorder("移除条件")
        
        // 选择列
        val columnLabel = JLabel("选择要检查的列:")
        columnLabel.font = Font("微软雅黑", Font.PLAIN, 11)
        removalGroup.add(columnLabel)
        
        targetColumnCombo = JComboBox<String>()
        targetColumnCombo!!.preferredSize = Dimension(200, 25)
        removalGroup.add(targetColumnCombo)
        removalGroup.add(Box.createVerticalStrut(5))
        
        // 移除条件
        val conditionLabel = JLabel("移除条件:")
        conditionLabel.font = Font("微软雅黑", Font.PLAIN, 11)
        removalGroup.add(conditionLabel)
        
        val conditionPanel = JPanel()
        conditionPanel.layout = FlowLayout(FlowLayout.LEFT)
        
        conditionCombo = JComboBox<String>(arrayOf("等于", "不等于", "包含", "不包含", "为空", "不为空"))
        conditionCombo!!.preferredSize = Dimension(80, 25)
        
        valueField = JTextField()
        valueField!!.preferredSize = Dimension(100, 25)
        valueField!!.toolTipText = "输入要匹配的值"
        
        conditionPanel.add(conditionCombo)
        conditionPanel.add(JLabel("值:"))
        conditionPanel.add(valueField)
        
        removalGroup.add(conditionPanel)
        removalGroup.add(Box.createVerticalStrut(5))
        
        // 其他选项
        val showRemovedCheckBox = JCheckBox("显示被移除的订单")
        val backupBeforeRemoveCheckBox = JCheckBox("移除前备份数据")
        
        removalGroup.add(showRemovedCheckBox)
        removalGroup.add(backupBeforeRemoveCheckBox)
        
        // 绑定列选择器更新事件
        bindColumnComboBoxes(targetColumnCombo!!, null)
        
        return removalGroup
    }
    
    private fun createButtonPanel(panel: JPanel): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout()
        
        val previewRemovalButton = JButton("预览移除")
        val executeRemovalButton = JButton("执行移除")
        val restoreButton = JButton("恢复数据")
        val downloadButton = JButton("下载当前预览")
        
        buttonPanel.add(previewRemovalButton)
        buttonPanel.add(executeRemovalButton)
        buttonPanel.add(restoreButton)
        buttonPanel.add(downloadButton)
        
        // 绑定按钮事件
        bindButtonEvents(panel, previewRemovalButton, executeRemovalButton, restoreButton, downloadButton)
        
        return buttonPanel
    }
    
    private fun bindColumnComboBoxes(plannedColumnCombo: JComboBox<String>, shippedColumnCombo: JComboBox<String>?) {
        // 更新列选择器
        fun updateColumnComboBoxes() {
            val currentTable = getCurrentTable()
            if (currentTable != null) {
                val headers = currentTable.headers
                plannedColumnCombo.model = DefaultComboBoxModel(headers.toTypedArray())
                shippedColumnCombo?.model = DefaultComboBoxModel(headers.toTypedArray())
                
                // 设置默认选择
                val plannedIndex = headers.indexOf("计划发货数量")
                val shippedIndex = headers.indexOf("已发货数")
                if (plannedIndex >= 0) plannedColumnCombo.selectedIndex = plannedIndex
                if (shippedIndex >= 0) shippedColumnCombo?.selectedIndex = shippedIndex
            } else {
                // 如果没有数据，清空下拉框
                plannedColumnCombo.model = DefaultComboBoxModel<String>()
                shippedColumnCombo?.model = DefaultComboBoxModel<String>()
            }
        }
        
        // 将更新函数保存到UIManager中，以便在数据更新时调用
        uiManager.addColumnUpdateListener { updateColumnComboBoxes() }
        
        // 初始化时更新列选择器
        updateColumnComboBoxes()
    }
    
    private fun bindButtonEvents(
        panel: JPanel,
        previewRemovalButton: JButton,
        executeRemovalButton: JButton,
        restoreButton: JButton,
        downloadButton: JButton
    ) {
        // 预览移除按钮事件
        previewRemovalButton.addActionListener {
            handlePreviewRemoval(panel)
        }
        
        // 执行移除按钮事件
        executeRemovalButton.addActionListener {
            handleExecuteRemoval(panel)
        }
        
        // 恢复按钮事件
        restoreButton.addActionListener {
            handleRestore(panel)
        }
        
        // 下载按钮事件
        downloadButton.addActionListener {
            uiManager.handleDownloadCurrentPreview(panel)
        }
    }
    
    private fun handlePreviewRemoval(panel: JPanel) {
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先导入并合并表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        // 获取用户设置的移除条件
        val targetColumnIndex = targetColumnCombo?.selectedIndex ?: -1
        val condition = conditionCombo?.selectedItem?.toString() ?: "等于"
        val value = valueField?.text ?: ""
        
        if (targetColumnIndex < 0) {
            JOptionPane.showMessageDialog(panel, "请选择要检查的列", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        if (condition != "为空" && condition != "不为空" && value.isBlank()) {
            JOptionPane.showMessageDialog(panel, "请输入要匹配的值", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            val smartMerger = SmartTableMerger()
            val previewResult = smartMerger.removeRowsByCondition(currentTable, targetColumnIndex, condition, value)
            
            // 只显示要移除的行的序号
            val removedSequences = previewResult.removedRows.map { row -> 
                if (row.isNotEmpty()) row[0] else "未知序号"
            }
            
            // 显示预览信息
            val columnName = currentTable.headers[targetColumnIndex]
            val previewText = buildString {
                appendLine("移除条件预览完成！")
                appendLine("检查列: $columnName")
                appendLine("移除条件: $condition ${if (value.isNotBlank()) "\"$value\"" else ""}")
                appendLine("原始行数: ${currentTable.rowCount}")
                appendLine("移除行数: ${previewResult.removedCount}")
                appendLine("剩余行数: ${previewResult.remainingCount}")
                appendLine()
                if (removedSequences.isNotEmpty()) {
                    appendLine("将要移除的行序号:")
                    removedSequences.forEach { seq ->
                        appendLine("- $seq")
                    }
                } else {
                    appendLine("没有符合条件的行需要移除")
                }
                appendLine()
                appendLine("点击'执行移除'按钮将实际移除这些行。")
            }
            
            JOptionPane.showMessageDialog(panel, previewText, "移除预览完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "预览失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleExecuteRemoval(panel: JPanel) {
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先导入并合并表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        // 获取用户设置的移除条件
        val targetColumnIndex = targetColumnCombo?.selectedIndex ?: -1
        val condition = conditionCombo?.selectedItem?.toString() ?: "等于"
        val value = valueField?.text ?: ""
        
        if (targetColumnIndex < 0) {
            JOptionPane.showMessageDialog(panel, "请选择要检查的列", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        if (condition != "为空" && condition != "不为空" && value.isBlank()) {
            JOptionPane.showMessageDialog(panel, "请输入要匹配的值", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            val smartMerger = SmartTableMerger()
            val removalResult = smartMerger.removeRowsByCondition(currentTable, targetColumnIndex, condition, value)
            
            // 实际更新数据源
            when {
                uiManager.smartMergeResult != null -> {
                    // 如果当前是智能合并结果，更新智能合并结果
                    uiManager.smartMergeResult = uiManager.smartMergeResult!!.copy(
                        updatedTable = removalResult.filteredTable
                    )
                }
                uiManager.mergedTable != null -> {
                    // 如果当前是普通合并结果，更新合并结果
                    uiManager.mergedTable = io.github.kotlin.fibonacci.model.MergedTableData(
                        sourceTables = uiManager.mergedTable!!.sourceTables,
                        headers = removalResult.filteredTable.headers,
                        rows = removalResult.filteredTable.rows,
                        formulas = removalResult.filteredTable.formulas
                    )
                }
                uiManager.importedTables.isNotEmpty() -> {
                    // 如果当前是原始导入数据，更新第一个导入的表格
                    uiManager.importedTables[0] = removalResult.filteredTable
                }
            }
            
            // 清除移除结果，因为数据已经实际更新
            uiManager.orderRemovalResult = null
            
            // 更新预览显示处理后的表格
            uiManager.updatePreview()
            
            val columnName = currentTable.headers[targetColumnIndex]
            JOptionPane.showMessageDialog(panel, 
                "移除完成！\n检查列: $columnName\n移除条件: $condition ${if (value.isNotBlank()) "\"$value\"" else ""}\n移除行数: ${removalResult.removedCount}\n剩余行数: ${removalResult.remainingCount}\n预览已更新为处理后的表格。", 
                "移除完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "移除失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleRestore(panel: JPanel) {
        if (uiManager.orderRemovalResult != null) {
            // 恢复原始数据
            if (uiManager.smartMergeResult != null) {
                uiManager.smartMergeResult = uiManager.smartMergeResult!!.copy(updatedTable = uiManager.orderRemovalResult!!.originalTable)
            } else if (uiManager.mergedTable != null) {
                uiManager.mergedTable = MergedTableData(
                    sourceTables = uiManager.mergedTable!!.sourceTables,
                    headers = uiManager.orderRemovalResult!!.originalTable.headers,
                    rows = uiManager.orderRemovalResult!!.originalTable.rows,
                    formulas = uiManager.orderRemovalResult!!.originalTable.formulas
                )
            }
            
            uiManager.orderRemovalResult = null
            uiManager.updatePreview()
            JOptionPane.showMessageDialog(panel, "数据已恢复", "恢复完成", JOptionPane.INFORMATION_MESSAGE)
        } else {
            JOptionPane.showMessageDialog(panel, "没有可恢复的数据", "提示", JOptionPane.WARNING_MESSAGE)
        }
    }
    
    private fun getCurrentTable(): TableData? {
        return when {
            uiManager.smartMergeResult != null -> uiManager.smartMergeResult!!.updatedTable
            uiManager.mergedTable != null -> {
                val mergedTable = uiManager.mergedTable!!
                TableData(
                    fileName = "合并结果",
                    headers = mergedTable.headers,
                    rows = mergedTable.rows,
                    formulas = mergedTable.formulas
                )
            }
            uiManager.importedTables.isNotEmpty() -> uiManager.importedTables[0]
            else -> null
        }
    }
    
    /**
     * 提供下载选项
     */
    private fun offerDownload(panel: JPanel, filteredTable: TableData) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        val lastDir = uiManager.lastSelectedDirectory
        if (lastDir != null && lastDir.exists()) {
            fileChooser.currentDirectory = lastDir
        }
        
        fileChooser.selectedFile = File("移除已完成订单_${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx")
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            uiManager.lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val exporter = io.github.kotlin.fibonacci.excel.ExcelExporter()
                val mergedTableData = MergedTableData(
                    sourceTables = listOf(filteredTable),
                    headers = filteredTable.headers,
                    rows = filteredTable.rows,
                    formulas = filteredTable.formulas
                )
                exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                
                JOptionPane.showMessageDialog(panel, "移除结果导出成功！\n文件保存为: ${fileChooser.selectedFile.name}", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
}
