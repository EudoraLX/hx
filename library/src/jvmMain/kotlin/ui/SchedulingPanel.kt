package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*
import java.io.File
import java.time.LocalDate

/**
 * 智能排产面板
 */
class SchedulingPanel(
    private val uiManager: UIManager,
    private val previewPanel: JPanel
) {
    
    // 策略选择按钮
    private lateinit var capacityFirstRadio: JRadioButton
    private lateinit var timeFirstRadio: JRadioButton
    private lateinit var orderFirstRadio: JRadioButton
    private lateinit var balancedRadio: JRadioButton
    
    fun createPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("智能排产")
        
        // 排产策略选择
        val strategyGroup = createStrategyGroup()
        panel.add(strategyGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 参数设置
        val paramGroup = createParameterGroup()
        panel.add(paramGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 约束条件
        val constraintGroup = createConstraintGroup()
        panel.add(constraintGroup)
        panel.add(Box.createVerticalStrut(10))
        
        // 操作按钮
        val buttonPanel = createButtonPanel(panel)
        panel.add(buttonPanel)
        
        return panel
    }
    
    private fun createStrategyGroup(): JPanel {
        val strategyGroup = JPanel()
        strategyGroup.layout = BoxLayout(strategyGroup, BoxLayout.Y_AXIS)
        strategyGroup.border = BorderFactory.createTitledBorder("排产策略")
        
        val strategyButtonGroup = ButtonGroup()
        val capacityFirstRadio = JRadioButton("产能优先", true)
        val timeFirstRadio = JRadioButton("时间优先")
        val orderFirstRadio = JRadioButton("订单优先(仅发货计划)")
        val balancedRadio = JRadioButton("平衡策略")
        
        strategyButtonGroup.add(capacityFirstRadio)
        strategyButtonGroup.add(timeFirstRadio)
        strategyButtonGroup.add(orderFirstRadio)
        strategyButtonGroup.add(balancedRadio)
        
        strategyGroup.add(capacityFirstRadio)
        strategyGroup.add(timeFirstRadio)
        strategyGroup.add(orderFirstRadio)
        strategyGroup.add(balancedRadio)
        
        // 存储策略选择按钮的引用，以便在排产时获取选择的策略
        this.capacityFirstRadio = capacityFirstRadio
        this.timeFirstRadio = timeFirstRadio
        this.orderFirstRadio = orderFirstRadio
        this.balancedRadio = balancedRadio
        
        return strategyGroup
    }
    
    private fun createParameterGroup(): JPanel {
        val paramGroup = JPanel()
        paramGroup.layout = BoxLayout(paramGroup, BoxLayout.Y_AXIS)
        paramGroup.border = BorderFactory.createTitledBorder("参数设置")
        
        val workDaysLabel = JLabel("工作日天数:")
        val workDaysSpinner = JSpinner(SpinnerNumberModel(22, 1, 31, 1))
        
        val shiftHoursLabel = JLabel("每班工作小时:")
        val shiftHoursSpinner = JSpinner(SpinnerNumberModel(8, 1, 24, 1))
        
        val bufferDaysLabel = JLabel("缓冲天数:")
        val bufferDaysSpinner = JSpinner(SpinnerNumberModel(2, 0, 10, 1))
        
        paramGroup.add(workDaysLabel)
        paramGroup.add(workDaysSpinner)
        paramGroup.add(Box.createVerticalStrut(5))
        paramGroup.add(shiftHoursLabel)
        paramGroup.add(shiftHoursSpinner)
        paramGroup.add(Box.createVerticalStrut(5))
        paramGroup.add(bufferDaysLabel)
        paramGroup.add(bufferDaysSpinner)
        
        return paramGroup
    }
    
    private fun createConstraintGroup(): JPanel {
        val constraintGroup = JPanel()
        constraintGroup.layout = BoxLayout(constraintGroup, BoxLayout.Y_AXIS)
        constraintGroup.border = BorderFactory.createTitledBorder("约束条件")
        
        val respectDeadlineCheckBox = JCheckBox("严格遵守交付期", true)
        val considerCapacityCheckBox = JCheckBox("考虑机台产能限制", true)
        val avoidOvertimeCheckBox = JCheckBox("避免加班生产")
        val balanceLoadCheckBox = JCheckBox("平衡机台负载", true)
        
        constraintGroup.add(respectDeadlineCheckBox)
        constraintGroup.add(considerCapacityCheckBox)
        constraintGroup.add(avoidOvertimeCheckBox)
        constraintGroup.add(balanceLoadCheckBox)
        
        return constraintGroup
    }
    
    private fun createButtonPanel(panel: JPanel): JPanel {
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout()
        
        val convertOrdersButton = JButton("转换订单")
        val generateScheduleButton = JButton("生成排产计划")
        val exportScheduleButton = JButton("导出排产")
        val downloadButton = JButton("下载当前预览")
        
        buttonPanel.add(convertOrdersButton)
        buttonPanel.add(generateScheduleButton)
        buttonPanel.add(exportScheduleButton)
        buttonPanel.add(downloadButton)
        
        // 绑定按钮事件
        bindButtonEvents(panel, convertOrdersButton, generateScheduleButton, exportScheduleButton, downloadButton)
        
        return buttonPanel
    }
    
    private fun bindButtonEvents(
        panel: JPanel,
        convertOrdersButton: JButton,
        generateScheduleButton: JButton,
        exportScheduleButton: JButton,
        downloadButton: JButton
    ) {
        // 转换订单按钮事件
        convertOrdersButton.addActionListener {
            handleConvertOrders(panel)
        }
        
        // 生成排产计划按钮事件
        generateScheduleButton.addActionListener {
            handleGenerateSchedule(panel)
        }
        
        // 导出排产按钮事件
        exportScheduleButton.addActionListener {
            handleExportSchedule(panel)
        }
        
        // 下载按钮事件
        downloadButton.addActionListener {
            uiManager.handleDownloadCurrentPreview(panel)
        }
    }
    
    private fun handleConvertOrders(panel: JPanel) {
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先导入表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 获取选择的排产策略
            val selectedStrategy = when {
                capacityFirstRadio.isSelected -> SchedulingStrategy.CAPACITY_FIRST
                timeFirstRadio.isSelected -> SchedulingStrategy.TIME_FIRST
                orderFirstRadio.isSelected -> SchedulingStrategy.ORDER_FIRST
                balancedRadio.isSelected -> SchedulingStrategy.BALANCED
                else -> SchedulingStrategy.ORDER_FIRST
            }
            
            // 检查表格是否包含发货计划信息
            val hasShippingPlan = checkIfHasShippingPlan(currentTable)
            
            val tableToConvert = if (hasShippingPlan) {
                // 如果表格包含发货计划信息，直接使用
                currentTable
            } else {
                // 如果没有发货计划信息，尝试合并发货计划表
                if (uiManager.shippingPlanTable != null) {
                    val flowManager = SchedulingFlowManager()
                    val mergedResult = flowManager.mergeTablesByCompanyModel(currentTable, uiManager.shippingPlanTable!!)
                    mergedResult
                } else {
                    currentTable
                }
            }
            
            val converter = OrderConverter()
            val allOrders = converter.convertToProductionOrders(tableToConvert)
            
            // 获取筛选后的订单（参与排产的订单）
            val flowManager = SchedulingFlowManager()
            val filteredOrders = flowManager.filterOrdersForScheduling(tableToConvert)
            val excludedOrders = flowManager.getExcludedOrders(tableToConvert)
            
            // 根据排产策略决定是否只保留发货计划表中的订单
            val ordersToConvert = if (selectedStrategy == SchedulingStrategy.ORDER_FIRST) {
                // 订单优先策略：只保留发货计划表中的订单
                val prioritizedOrders = flowManager.adjustPriorityFromTable(filteredOrders, tableToConvert)
                prioritizedOrders.filter { it.priority == OrderPriority.URGENT }
            } else {
                // 其他策略：使用筛选后的订单
                filteredOrders
            }
            
            // 保存所有原始订单，用于显示和绿色标注
            uiManager.productionOrders.clear()
            uiManager.productionOrders.addAll(allOrders)
            
            // 保存筛选后的订单和排除的订单，用于预览显示
            uiManager.filteredOrders.clear()
            uiManager.filteredOrders.addAll(filteredOrders)
            
            // 保存排产流程结果，包含排除的订单信息
            uiManager.schedulingFlowResult = SchedulingFlowResult(
                mergedTable = tableToConvert,
                filteredOrders = filteredOrders,
                excludedOrders = excludedOrders,
                schedulingResult = SchedulingResult(
                    orders = emptyList(),
                    machineSchedule = emptyMap(),
                    totalProductionDays = 0,
                    utilizationRate = 0.0,
                    onTimeDeliveryRate = 0.0
                ),
                schedulingPlanTable = TableData("", emptyList(), emptyList(), emptyList())
            )
            
            // 立即更新预览显示转换结果
            uiManager.updatePreview()
            
            // 自动切换到筛选结果标签页
            uiManager.previewTabbedPane?.selectedIndex = 1 // 筛选结果是第2个标签页（索引1）
            
            val message = buildString {
                appendLine("订单转换完成！")
                appendLine("原始订单数: ${allOrders.size}")
                appendLine("筛选后订单数: ${filteredOrders.size}")
                appendLine("排除订单数: ${excludedOrders.size}")
                appendLine("参与排产订单数: ${ordersToConvert.size}")
                appendLine()
                appendLine("排除原因：")
                appendLine("- 已完成/改制: ${excludedOrders.count { it.notes?.contains("已完成") == true || it.notes?.contains("改制") == true }}")
                appendLine("- 外径为0: ${excludedOrders.count { it.outerDiameter <= 0 }}")
                appendLine("- 注射完成>=未发货数: ${excludedOrders.count { (it.injectionCompleted ?: 0) >= it.unshippedQuantity }}")
                appendLine("- 日产量为空: ${excludedOrders.count { it.dailyProduction <= 0 }}")
                appendLine()
                appendLine("已自动显示筛选结果，排除的订单用绿色标注")
            }
            
            JOptionPane.showMessageDialog(panel, message, "转换完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "转换失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleGenerateSchedule(panel: JPanel) {
        // 检查是否有导入的表格
        val currentTable = getCurrentTable()
        if (currentTable == null) {
            JOptionPane.showMessageDialog(panel, "请先在文件管理处导入表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 获取选择的排产策略
            val selectedStrategy = when {
                capacityFirstRadio.isSelected -> SchedulingStrategy.CAPACITY_FIRST
                timeFirstRadio.isSelected -> SchedulingStrategy.TIME_FIRST
                orderFirstRadio.isSelected -> SchedulingStrategy.ORDER_FIRST
                balancedRadio.isSelected -> SchedulingStrategy.BALANCED
                else -> SchedulingStrategy.ORDER_FIRST
            }
            
            // 直接使用导入的表格进行排产
            val flowManager = SchedulingFlowManager()
            val machineRules = uiManager.getMachineRules()
            
            // 检查表格是否包含发货计划信息（通过备注或其他字段判断）
            val hasShippingPlan = checkIfHasShippingPlan(currentTable)
            
            val flowResult = if (hasShippingPlan) {
                // 如果表格包含发货计划信息，直接排产
                flowManager.executeSchedulingFlowWithSingleTable(currentTable, machineRules, selectedStrategy)
            } else {
                // 如果没有发货计划信息，使用默认排产
                flowManager.executeSchedulingFlowWithSingleTable(currentTable, machineRules, selectedStrategy)
            }
            
            // 保存排产流程结果
            uiManager.schedulingFlowResult = flowResult
            uiManager.schedulingResult = flowResult.schedulingResult
            
            // 更新预览显示排产结果
            uiManager.updatePreview()
            
            // 自动切换到排产计划表标签页
            uiManager.previewTabbedPane?.selectedIndex = 4 // 排产计划表是第5个标签页（索引4）
            
            val stats = flowResult.schedulingResult.orders.size
            val strategyMessage = when (selectedStrategy) {
                SchedulingStrategy.ORDER_FIRST -> "\n注意：订单优先策略只处理发货计划表中的订单"
                else -> ""
            }
            
            JOptionPane.showMessageDialog(panel, 
                "排产完成！\n总订单数: $stats\n机台利用率: ${String.format("%.1f", flowResult.schedulingResult.utilizationRate * 100)}%\n按时交付率: ${String.format("%.1f", flowResult.schedulingResult.onTimeDeliveryRate * 100)}%$strategyMessage\n\n已自动显示排产计划表", 
                "排产完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "排产失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    /**
     * 检查表格是否包含发货计划信息
     */
    private fun checkIfHasShippingPlan(table: TableData): Boolean {
        // 检查是否有发货计划相关的列
        val shippingPlanColumns = listOf("客户合同号", "合同号", "签订客户", "客户名称", "客户型号", "业务员", "交货时间")
        return shippingPlanColumns.any { column -> table.headers.contains(column) }
    }
    
    private fun handleExportSchedule(panel: JPanel) {
        if (uiManager.schedulingResult == null) {
            JOptionPane.showMessageDialog(panel, "请先生成排产计划", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        val lastDir: File? = uiManager.lastSelectedDirectory
        if (lastDir != null && lastDir.exists()) {
            fileChooser.currentDirectory = lastDir
        }
        
        fileChooser.selectedFile = File("排产计划_${LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx")
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            uiManager.lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val converter = OrderConverter()
                val tableData = converter.convertToTableData(uiManager.schedulingResult!!.orders, listOf(
                    "序号", "公司型号", "计划发货时间", "计划发货数量", "数量（支）", 
                    "交付期", "日产量", "生产天数", "剩余天数", "已发货数", "未发数量", 
                    "机台", "计划开始时间", "计划完成时间", "优先级", "状态", "备注"
                ))
                
                val exporter = io.github.kotlin.fibonacci.excel.ExcelExporter()
                val mergedTableData = MergedTableData(
                    sourceTables = listOf(tableData),
                    headers = tableData.headers,
                    rows = tableData.rows,
                    formulas = tableData.formulas
                )
                exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                
                JOptionPane.showMessageDialog(panel, "排产计划导出成功！", "成功", JOptionPane.INFORMATION_MESSAGE)
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
