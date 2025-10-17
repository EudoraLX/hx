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
        val orderFirstRadio = JRadioButton("订单优先")
        val balancedRadio = JRadioButton("平衡策略")
        
        strategyButtonGroup.add(capacityFirstRadio)
        strategyButtonGroup.add(timeFirstRadio)
        strategyButtonGroup.add(orderFirstRadio)
        strategyButtonGroup.add(balancedRadio)
        
        strategyGroup.add(capacityFirstRadio)
        strategyGroup.add(timeFirstRadio)
        strategyGroup.add(orderFirstRadio)
        strategyGroup.add(balancedRadio)
        
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
            JOptionPane.showMessageDialog(panel, "请先导入并合并表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            val converter = OrderConverter()
            uiManager.productionOrders.clear()
            uiManager.productionOrders.addAll(converter.convertToProductionOrders(currentTable))
            
            JOptionPane.showMessageDialog(panel, 
                "订单转换完成！\n转换订单数: ${uiManager.productionOrders.size}", 
                "转换完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "转换失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleGenerateSchedule(panel: JPanel) {
        if (uiManager.productionOrders.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请先转换订单数据", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 简化处理，使用平衡策略
            val strategy = SchedulingStrategy.BALANCED
            val constraints = SchedulingConstraints(
                workDaysPerMonth = 22,
                shiftHours = 8,
                bufferDays = 2,
                respectDeadline = true,
                considerCapacity = true,
                avoidOvertime = false,
                balanceLoad = true
            )
            
            val converter = OrderConverter()
            val machines = converter.createDefaultMachines()
            
            val scheduler = SmartScheduler()
            uiManager.schedulingResult = scheduler.schedule(uiManager.productionOrders, strategy, constraints, machines)
            
            // 更新预览显示排产结果
            uiManager.updatePreview()
            
            val stats = scheduler.generateStatistics(uiManager.schedulingResult!!.orders)
            JOptionPane.showMessageDialog(panel, 
                "排产完成！\n总订单数: ${stats.totalOrders}\n机台利用率: ${String.format("%.1f", uiManager.schedulingResult!!.utilizationRate * 100)}%\n按时交付率: ${String.format("%.1f", uiManager.schedulingResult!!.onTimeDeliveryRate * 100)}%", 
                "排产完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "排产失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
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
