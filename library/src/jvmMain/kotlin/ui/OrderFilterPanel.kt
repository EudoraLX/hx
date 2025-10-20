package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 订单筛选面板
 */
class OrderFilterPanel(
    private val uiManager: UIManager,
    private val previewPanel: JPanel
) {
    
    private val orderFilter = OrderFilter()
    private var filteredOrders = mutableListOf<ProductionOrder>()
    
    fun createPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("订单筛选")
        
        // 数据源选择
        val dataSourceGroup = createDataSourceGroup()
        panel.add(dataSourceGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 筛选条件
        val filterGroup = createFilterGroup()
        panel.add(filterGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 操作按钮
        val buttonPanel = createButtonPanel(panel)
        panel.add(buttonPanel)
        
        return panel
    }
    
    private fun createDataSourceGroup(): JPanel {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.border = BorderFactory.createTitledBorder("数据源选择")
        
        val dataSourceGroup = ButtonGroup()
        val outputTableRadio = JRadioButton("输出表", true)
        val schedulingTableRadio = JRadioButton("排产表")
        val shippingPlanRadio = JRadioButton("发货计划表")
        
        dataSourceGroup.add(outputTableRadio)
        dataSourceGroup.add(schedulingTableRadio)
        dataSourceGroup.add(shippingPlanRadio)
        
        group.add(outputTableRadio)
        group.add(schedulingTableRadio)
        group.add(shippingPlanRadio)
        
        return group
    }
    
    private fun createFilterGroup(): JPanel {
        val group = JPanel()
        group.layout = BoxLayout(group, BoxLayout.Y_AXIS)
        group.border = BorderFactory.createTitledBorder("筛选条件")
        
        // 完成状态筛选
        val statusGroup = JPanel()
        statusGroup.layout = FlowLayout(FlowLayout.LEFT)
        statusGroup.add(JLabel("完成状态:"))
        val statusCombo = JComboBox<String>()
        statusCombo.addItem("全部")
        statusCombo.addItem("未完成")
        statusCombo.addItem("已完成")
        statusCombo.addItem("生产中")
        statusGroup.add(statusCombo)
        group.add(statusGroup)
        
        // 优先级筛选
        val priorityGroup = JPanel()
        priorityGroup.layout = FlowLayout(FlowLayout.LEFT)
        priorityGroup.add(JLabel("优先级:"))
        val priorityCombo = JComboBox<String>()
        priorityCombo.addItem("全部")
        priorityCombo.addItem("紧急")
        priorityCombo.addItem("高")
        priorityCombo.addItem("中")
        priorityCombo.addItem("低")
        priorityGroup.add(priorityCombo)
        group.add(priorityGroup)
        
        // 机台筛选
        val machineGroup = JPanel()
        machineGroup.layout = FlowLayout(FlowLayout.LEFT)
        machineGroup.add(JLabel("机台:"))
        val machineCombo = JComboBox<String>()
        machineCombo.addItem("全部")
        machineCombo.addItem("1#")
        machineCombo.addItem("2#")
        machineCombo.addItem("3#")
        machineCombo.addItem("4#")
        machineCombo.addItem("5#")
        machineCombo.addItem("6#")
        machineCombo.addItem("7#")
        machineGroup.add(machineCombo)
        group.add(machineGroup)
        
        // 时间范围筛选
        val timeGroup = JPanel()
        timeGroup.layout = FlowLayout(FlowLayout.LEFT)
        timeGroup.add(JLabel("交付期范围:"))
        val startDateField = JTextField(10)
        startDateField.text = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        timeGroup.add(JLabel("从"))
        timeGroup.add(startDateField)
        val endDateField = JTextField(10)
        endDateField.text = LocalDate.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        timeGroup.add(JLabel("到"))
        timeGroup.add(endDateField)
        group.add(timeGroup)
        
        // 内外径范围筛选
        val diameterGroup = JPanel()
        diameterGroup.layout = FlowLayout(FlowLayout.LEFT)
        diameterGroup.add(JLabel("外径范围:"))
        val minDiameterField = JTextField(5)
        minDiameterField.text = "0"
        diameterGroup.add(minDiameterField)
        diameterGroup.add(JLabel("-"))
        val maxDiameterField = JTextField(5)
        maxDiameterField.text = "1000"
        diameterGroup.add(maxDiameterField)
        group.add(diameterGroup)
        
        return group
    }
    
    private fun createButtonPanel(panel: JPanel): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout()
        
        val previewButton = JButton("预览筛选结果")
        val applyButton = JButton("应用筛选")
        val resetButton = JButton("重置")
        val removeButton = JButton("移除选中订单")
        val exportButton = JButton("导出筛选结果")
        
        buttonPanel.add(previewButton)
        buttonPanel.add(applyButton)
        buttonPanel.add(resetButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(exportButton)
        
        // 绑定按钮事件
        previewButton.addActionListener {
            handlePreviewFilter(panel)
        }
        
        applyButton.addActionListener {
            handleApplyFilter(panel)
        }
        
        resetButton.addActionListener {
            handleResetFilter(panel)
        }
        
        removeButton.addActionListener {
            handleRemoveOrders(panel)
        }
        
        exportButton.addActionListener {
            handleExportFilter(panel)
        }
        
        return buttonPanel
    }
    
    private fun handlePreviewFilter(panel: JPanel) {
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先导入表格数据", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 获取未完成订单
            val incompleteOrders = orderFilter.filterIncompleteOrders(currentTable)
            
            // 显示预览信息
            val message = buildString {
                appendLine("筛选预览结果：")
                appendLine("原始订单数: ${currentTable.rowCount}")
                appendLine("未完成订单数: ${incompleteOrders.size}")
                appendLine("已完成订单数: ${currentTable.rowCount - incompleteOrders.size}")
                appendLine()
                appendLine("未完成订单详情：")
                incompleteOrders.take(5).forEach { order ->
                    appendLine("序号: ${order.id}, 公司型号: ${order.companyModel}, 未发数量: ${order.unshippedQuantity}")
                }
                if (incompleteOrders.size > 5) {
                    appendLine("... 还有 ${incompleteOrders.size - 5} 个订单")
                }
            }
            
            JOptionPane.showMessageDialog(panel, message, "筛选预览", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "筛选预览失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleApplyFilter(panel: JPanel) {
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先导入表格数据", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 获取未完成订单
            filteredOrders.clear()
            val incompleteOrders = orderFilter.filterIncompleteOrders(currentTable)
            filteredOrders.addAll(incompleteOrders)
            
            // 将筛选结果存储到UIManager中
            uiManager.filteredOrders = filteredOrders
            
            // 更新预览显示
            uiManager.updatePreview()
            
            JOptionPane.showMessageDialog(panel, 
                "筛选完成！\n筛选出 ${filteredOrders.size} 个未完成订单\n\n请查看右侧预览区域的'筛选结果'标签页", 
                "筛选完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "筛选失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleResetFilter(panel: JPanel) {
        filteredOrders.clear()
        uiManager.updatePreview()
        JOptionPane.showMessageDialog(panel, "筛选条件已重置", "重置完成", JOptionPane.INFORMATION_MESSAGE)
    }
    
    private fun handleRemoveOrders(panel: JPanel) {
        if (filteredOrders.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有可移除的订单", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val result = JOptionPane.showConfirmDialog(
            panel, 
            "确定要移除筛选出的 ${filteredOrders.size} 个订单吗？", 
            "确认移除", 
            JOptionPane.YES_NO_OPTION
        )
        
        if (result == JOptionPane.YES_OPTION) {
            try {
                val currentTable = getCurrentTable()
                if (currentTable != null) {
                    val smartMerger = SmartTableMerger()
                    val orderIdsToRemove = filteredOrders.map { it.id }
                    
                    // 移除订单
                    val removalResult = smartMerger.removeRowsByOrderIds(currentTable, orderIdsToRemove)
                    
                    // 更新UI
                    uiManager.smartMergeResult = MergeResult(
                        originalTable = currentTable,
                        updatedTable = removalResult.filteredTable,
                        changes = emptyList()
                    )
                    uiManager.updatePreview()
                    
                    JOptionPane.showMessageDialog(panel, 
                        "已移除 ${removalResult.removedCount} 个订单", 
                        "移除完成", 
                        JOptionPane.INFORMATION_MESSAGE)
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "移除失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    private fun handleExportFilter(panel: JPanel) {
        if (filteredOrders.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "没有筛选结果可导出", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        fileChooser.selectedFile = java.io.File("筛选结果_${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx")
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                val converter = OrderConverter()
                val headers = listOf(
                    "序号", "公司型号", "客户型号", "计划发货时间", "计划发货数量", 
                    "数量（支）", "交付期", "内径", "外径", "日产量", "生产天数", 
                    "剩余天数", "已发货数", "未发数量", "机台", "管子情况", 
                    "优先级", "状态", "备注"
                )
                val tableData = converter.convertToTableData(filteredOrders, headers)
                
                val exporter = io.github.kotlin.fibonacci.excel.ExcelExporter()
                val mergedTableData = MergedTableData(
                    sourceTables = listOf(tableData),
                    headers = tableData.headers,
                    rows = tableData.rows,
                    formulas = tableData.formulas
                )
                exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                
                JOptionPane.showMessageDialog(panel, "筛选结果导出成功！", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
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
}
