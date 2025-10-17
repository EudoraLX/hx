package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*
import java.io.File

/**
 * UI管理器 - 负责管理所有UI组件的创建和交互
 */
class UIManager {
    
    // 全局变量
    var lastSelectedDirectory: File? = null
    var importedTables = mutableListOf<TableData>()
    var mergedTable: MergedTableData? = null
    var smartMergeResult: MergeResult? = null
    var orderRemovalResult: OrderRemovalResult? = null
    var schedulingResult: SchedulingResult? = null
    var productionOrders = mutableListOf<ProductionOrder>()
    var filteredOrders = mutableListOf<ProductionOrder>()
    
    // 数据源管理
    private var dataSourceManager = DataSourceManager()
    private var currentDataSource: String? = null
    
    // UI组件引用
    private var previewInfoLabel: JLabel? = null
    private var previewTable: JTable? = null
    private var statusLabel: JLabel? = null
    private var enhancedPreviewManager = EnhancedPreviewManager()
    private var previewTabbedPane: JTabbedPane? = null
    private var dataSourceComboBox: JComboBox<String>? = null
    
    // 列更新监听器
    private var columnUpdateListeners = mutableListOf<() -> Unit>()
    
    /**
     * 创建主窗口
     */
    fun createAndShowGUI() {
        val frame = JFrame("Excel 表格合并器")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1200, 800)
        frame.setLocationRelativeTo(null)
        
        frame.contentPane.add(createMainPanel())
        frame.isVisible = true
    }
    
    /**
     * 创建主面板
     */
    private fun createMainPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        
        // 标题
        val titleLabel = JLabel("Excel 表格合并器", SwingConstants.CENTER)
        titleLabel.font = Font("微软雅黑", Font.BOLD, 18)
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // 主内容区域
        val mainPanel = JPanel()
        mainPanel.layout = BorderLayout()
        
        // 右侧预览区域
        val previewPanel = createPreviewPanel()
        mainPanel.add(previewPanel, BorderLayout.CENTER)
        
        // 左侧控制面板
        val controlPanel = createControlPanel(previewPanel)
        mainPanel.add(controlPanel, BorderLayout.WEST)
        
        panel.add(mainPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建控制面板
     */
    private fun createControlPanel(previewPanel: JPanel): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.preferredSize = Dimension(350, 0)
        
        // 创建标签页面板
        val tabbedPane = JTabbedPane()
        tabbedPane.tabPlacement = JTabbedPane.TOP
        
        // 标签页1: 文件管理
        tabbedPane.addTab("文件管理", FileManagementPanel(this, previewPanel).createPanel())
        
        // 标签页2: 订单筛选
        tabbedPane.addTab("订单筛选", OrderFilterPanel(this, previewPanel).createPanel())
        
        // 标签页3: 机台配置
        tabbedPane.addTab("机台配置", MachineConfigPanel(this, previewPanel).createPanel())
        
        // 标签页4: 订单移除
        tabbedPane.addTab("订单移除", OrderRemovalPanel(this, previewPanel).createPanel())
        
        // 标签页5: 智能排产
        tabbedPane.addTab("智能排产", SchedulingPanel(this, previewPanel).createPanel())
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建预览面板
     */
    private fun createPreviewPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.border = BorderFactory.createTitledBorder("数据预览")
        
        // 创建数据源选择器
        val dataSourcePanel = createDataSourcePanel()
        panel.add(dataSourcePanel, BorderLayout.NORTH)
        
        // 创建标签页面板
        val tabbedPane = JTabbedPane()
        tabbedPane.tabPlacement = JTabbedPane.TOP
        this.previewTabbedPane = tabbedPane
        
        // 原始数据标签页
        val originalDataPanel = createDataPreviewPanel("原始数据", null)
        tabbedPane.addTab("原始数据", originalDataPanel)
        
        // 筛选结果标签页
        val filteredDataPanel = createDataPreviewPanel("筛选结果", null)
        tabbedPane.addTab("筛选结果", filteredDataPanel)
        
        // 机台配置标签页
        val machineConfigPanel = createMachineConfigPreviewPanel()
        tabbedPane.addTab("机台配置", machineConfigPanel)
        
        // 排产结果标签页
        val schedulingResultPanel = createSchedulingResultPreviewPanel()
        tabbedPane.addTab("排产结果", schedulingResultPanel)
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        // 保存组件引用
        this.previewInfoLabel = JLabel("请先导入并合并表格")
        this.previewTable = JTable()
        
        return panel
    }
    
    /**
     * 创建数据源选择面板
     */
    private fun createDataSourcePanel(): JPanel {
        val panel = JPanel()
        panel.layout = FlowLayout(FlowLayout.LEFT)
        
        val label = JLabel("数据源:")
        val comboBox = JComboBox<String>()
        comboBox.addItem("请选择数据源")
        this.dataSourceComboBox = comboBox
        
        // 绑定选择事件
        comboBox.addActionListener {
            val selectedSource = comboBox.selectedItem as? String
            if (selectedSource != null && selectedSource != "请选择数据源") {
                currentDataSource = selectedSource
                updatePreview()
            }
        }
        
        panel.add(label)
        panel.add(comboBox)
        
        return panel
    }
    
    /**
     * 创建数据预览面板
     */
    private fun createDataPreviewPanel(title: String, data: TableData?): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        
        // 预览信息标签
        val infoLabel = JLabel("$title - 请先导入数据")
        infoLabel.font = Font("微软雅黑", Font.PLAIN, 12)
        infoLabel.horizontalAlignment = SwingConstants.CENTER
        panel.add(infoLabel, BorderLayout.NORTH)
        
        // 表格显示区域
        val table = JTable()
        val scrollPane = JScrollPane(table)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建机台配置预览面板
     */
    private fun createMachineConfigPreviewPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        
        // 机台配置信息标签
        val infoLabel = JLabel("机台配置 - 显示完整的机台划分规则")
        infoLabel.font = Font("微软雅黑", Font.PLAIN, 12)
        infoLabel.horizontalAlignment = SwingConstants.CENTER
        panel.add(infoLabel, BorderLayout.NORTH)
        
        // 机台配置表格
        val table = JTable()
        val scrollPane = JScrollPane(table)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        
        // 设置机台配置表格模型
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = 5
            override fun getRowCount(): Int = 0
            override fun getValueAt(row: Int, col: Int): Any = ""
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
        table.model = model
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建排产结果预览面板
     */
    private fun createSchedulingResultPreviewPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        
        // 排产结果信息标签
        val infoLabel = JLabel("排产结果 - 显示智能排产计划")
        infoLabel.font = Font("微软雅黑", Font.PLAIN, 12)
        infoLabel.horizontalAlignment = SwingConstants.CENTER
        panel.add(infoLabel, BorderLayout.NORTH)
        
        // 排产结果表格
        val table = JTable()
        val scrollPane = JScrollPane(table)
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 更新预览显示
     */
    fun updatePreview() {
        val previewTabbedPane = this.previewTabbedPane ?: return
        
        // 触发列更新
        triggerColumnUpdate()
        
        // 更新各个标签页
        updateOriginalDataTab()
        updateFilteredDataTab()
        updateMachineConfigTab()
        updateSchedulingResultTab()
    }
    
    /**
     * 强制刷新机台配置预览
     */
    fun refreshMachineConfigPreview() {
        updateMachineConfigTab()
    }
    
    /**
     * 更新原始数据标签页
     */
    private fun updateOriginalDataTab() {
        val previewTabbedPane = this.previewTabbedPane ?: return
        val originalDataPanel = previewTabbedPane.getComponentAt(0) as? JPanel ?: return
        val table = findTableInPanel(originalDataPanel) ?: return
        
        val currentTable = getCurrentTable()
        enhancedPreviewManager.updateOriginalDataPreview(table, currentTable)
    }
    
    /**
     * 更新筛选结果标签页
     */
    private fun updateFilteredDataTab() {
        val previewTabbedPane = this.previewTabbedPane ?: return
        val filteredDataPanel = previewTabbedPane.getComponentAt(1) as? JPanel ?: return
        val table = findTableInPanel(filteredDataPanel) ?: return
        
        // 显示筛选后的订单
        val ordersToShow = if (filteredOrders.isNotEmpty()) {
            filteredOrders
        } else if (orderRemovalResult != null) {
            val converter = OrderConverter()
            converter.convertToProductionOrders(orderRemovalResult!!.filteredTable)
        } else {
            emptyList()
        }
        
        enhancedPreviewManager.updateFilteredDataPreview(table, ordersToShow)
    }
    
    /**
     * 更新机台配置标签页
     */
    private fun updateMachineConfigTab() {
        val previewTabbedPane = this.previewTabbedPane ?: return
        val machineConfigPanel = previewTabbedPane.getComponentAt(2) as? JPanel ?: return
        val table = findTableInPanel(machineConfigPanel) ?: return
        
        enhancedPreviewManager.updateMachineConfigPreview(table)
    }
    
    /**
     * 更新排产结果标签页
     */
    private fun updateSchedulingResultTab() {
        val previewTabbedPane = this.previewTabbedPane ?: return
        val schedulingResultPanel = previewTabbedPane.getComponentAt(3) as? JPanel ?: return
        val table = findTableInPanel(schedulingResultPanel) ?: return
        
        enhancedPreviewManager.updateSchedulingResultPreview(table, schedulingResult)
    }
    
    /**
     * 在面板中查找表格组件
     */
    private fun findTableInPanel(panel: JPanel): JTable? {
        for (component in panel.components) {
            if (component is JScrollPane) {
                val viewport = component.viewport
                if (viewport.view is JTable) {
                    return viewport.view as JTable
                }
            }
        }
        return null
    }
    
    /**
     * 获取当前表格数据
     */
    private fun getCurrentTable(): TableData? {
        // 如果选择了特定数据源，优先使用
        if (currentDataSource != null) {
            return dataSourceManager.getDataSource(currentDataSource!!)
        }
        
        // 否则使用默认逻辑
        return when {
            smartMergeResult != null -> smartMergeResult!!.updatedTable
            mergedTable != null -> {
                val mergedTable = this.mergedTable!!
                TableData(
                    fileName = "合并结果",
                    headers = mergedTable.headers,
                    rows = mergedTable.rows,
                    formulas = mergedTable.formulas
                )
            }
            importedTables.isNotEmpty() -> importedTables[0]
            else -> null
        }
    }
    
    /**
     * 添加数据源
     */
    fun addDataSource(name: String, tableData: TableData) {
        dataSourceManager.addDataSource(name, tableData)
        updateDataSourceComboBox()
    }
    
    /**
     * 更新数据源下拉框
     */
    private fun updateDataSourceComboBox() {
        val comboBox = dataSourceComboBox ?: return
        comboBox.removeAllItems()
        comboBox.addItem("请选择数据源")
        
        dataSourceManager.getDataSourceNames().forEach { name ->
            comboBox.addItem(name)
        }
    }
    
    /**
     * 设置当前数据源
     */
    fun setCurrentDataSource(name: String) {
        currentDataSource = name
        updatePreview()
    }
    
    /**
     * 获取所有数据源
     */
    fun getAllDataSources(): Map<String, TableData> {
        return dataSourceManager.getDataSourceNames().associateWith { name ->
            dataSourceManager.getDataSource(name)!!
        }
    }
    
    /**
     * 调整表格列宽
     */
    private fun adjustColumnWidths(table: JTable) {
        val tableColumnModel = table.columnModel
        for (i in 0 until tableColumnModel.columnCount) {
            val column = tableColumnModel.getColumn(i)
            column.preferredWidth = calculateOptimalColumnWidth(table, i)
            column.minWidth = 80
        }
    }
    
    /**
     * 计算最优列宽
     */
    private fun calculateOptimalColumnWidth(table: JTable, columnIndex: Int): Int {
        val column = table.columnModel.getColumn(columnIndex)
        val headerWidth = table.getTableHeader().getFontMetrics(table.getTableHeader().font)
            .stringWidth(column.headerValue.toString()) + 20
        
        var maxWidth = headerWidth
        for (row in 0 until table.rowCount) {
            val cellValue = table.getValueAt(row, columnIndex)?.toString() ?: ""
            val cellWidth = table.getFontMetrics(table.font).stringWidth(cellValue) + 20
            maxWidth = maxOf(maxWidth, cellWidth)
        }
        
        return minOf(maxWidth, 300)
    }
    
    /**
     * 设置状态标签
     */
    fun setStatusLabel(label: JLabel) {
        this.statusLabel = label
    }
    
    /**
     * 更新状态
     */
    fun updateStatus(message: String) {
        statusLabel?.text = message
    }
    
    /**
     * 添加列更新监听器
     */
    fun addColumnUpdateListener(listener: () -> Unit) {
        columnUpdateListeners.add(listener)
    }
    
    /**
     * 触发列更新
     */
    fun triggerColumnUpdate() {
        columnUpdateListeners.forEach { it() }
    }
    
    /**
     * 计算公式
     */
    private fun calculateFormula(formula: String, table: Any, row: Int, col: Int): String {
        // 简单的公式计算，支持基本的数学运算
        var result = formula
        
        // 处理常见的Excel公式模式
        if (formula.startsWith("=")) {
            result = result.substring(1) // 移除等号
        } else {
            // 如果没有等号，直接处理公式
            result = formula
        }
            
            // 处理SUM函数
            if (result.startsWith("SUM(") && result.endsWith(")")) {
                val sumRange = result.substring(4, result.length - 1) // 移除SUM(和)
                return calculateSumRange(sumRange, table, row)
            }
            
            // 处理未发货数 = 数量 - 已发货数 这样的公式
            if (result.contains("-") && (result.contains("已发货数") || result.contains("已发货")) && 
                (result.contains("数量") || result.contains("总数量"))) {
                // 获取headers和getValue方法
                val headers = when (table) {
                    is io.github.kotlin.fibonacci.model.TableData -> table.headers
                    is io.github.kotlin.fibonacci.model.MergedTableData -> table.headers
                    else -> return result
                }
                
                val getValue = { row: Int, col: Int ->
                    when (table) {
                        is io.github.kotlin.fibonacci.model.TableData -> table.getValue(row, col)
                        is io.github.kotlin.fibonacci.model.MergedTableData -> table.getValue(row, col)
                        else -> ""
                    }
                }
                
                // 查找相关列
                val quantityCol = headers.indexOfFirst { 
                    it.contains("数量") && !it.contains("已发货") && !it.contains("未发货") 
                }
                val shippedCol = headers.indexOfFirst { 
                    it.contains("已发货") 
                }
                
                if (quantityCol >= 0 && shippedCol >= 0) {
                    val quantity = getValue(row, quantityCol).toDoubleOrNull() ?: 0.0
                    val shipped = getValue(row, shippedCol).toDoubleOrNull() ?: 0.0
                    val unshipped = quantity - shipped
                    return unshipped.toInt().toString()
                }
            }
            
        // 处理其他常见的数学表达式
        try {
            // 处理单元格引用，如 E2, F2, $E2, $F2 等
            val cellRefPattern = Regex("\\$?[A-Z]+\\d+")
            val cellRefs = cellRefPattern.findAll(result)
            
            var processedExpression = result
            for (cellRef in cellRefs) {
                val ref = cellRef.value
                val cleanRef = ref.replace("$", "") // 移除$符号
                val colLetter = cleanRef.filter { it.isLetter() }
                val rowNum = cleanRef.filter { it.isDigit() }.toInt() - 1 // Excel行号从1开始，数组从0开始
                
                // 将列字母转换为数字
                val colNum = colLetterToNumber(colLetter)
                
                if (colNum >= 0 && rowNum >= 0) {
                    val getValue = { row: Int, col: Int ->
                        when (table) {
                            is io.github.kotlin.fibonacci.model.TableData -> table.getValue(row, col)
                            is io.github.kotlin.fibonacci.model.MergedTableData -> table.getValue(row, col)
                            else -> ""
                        }
                    }
                    val cellValue = getValue(rowNum, colNum)
                    processedExpression = processedExpression.replace(ref, cellValue)
                }
            }
            
            // 尝试计算数学表达式
            if (processedExpression.matches(Regex(".*[+\\-*/].*"))) {
                return evaluateMathExpression(processedExpression)
            } else if (processedExpression.matches(Regex("^\\d+$"))) {
                // 如果处理后的表达式是纯数字，直接返回
                return processedExpression
            }
            
            return result
        } catch (e: Exception) {
            return formula
        }
        
        return result
    }
    
    /**
     * 计算SUM函数
     */
    private fun calculateSumRange(range: String, table: Any, row: Int): String {
        try {
            // 处理范围，如 W2:Z2
            if (range.contains(":")) {
                val parts = range.split(":")
                if (parts.size != 2) {
                    return "0"
                }
                
                val startCell = parts[0].trim()
                val endCell = parts[1].trim()
                
                // 提取列字母和行号
                val startColLetters = startCell.filter { it.isLetter() }
                val endColLetters = endCell.filter { it.isLetter() }
                
                // 转换为列索引
                val startCol = colLetterToNumber(startColLetters)
                val endCol = colLetterToNumber(endColLetters)
                
                val getValue = { row: Int, col: Int ->
                    when (table) {
                        is io.github.kotlin.fibonacci.model.TableData -> table.getValue(row, col)
                        is io.github.kotlin.fibonacci.model.MergedTableData -> table.getValue(row, col)
                        else -> ""
                    }
                }
                
                var sum = 0.0
                // 确保列索引有效
                if (startCol >= 0 && endCol >= 0 && startCol <= endCol) {
                    for (col in startCol..endCol) {
                        val value = getValue(row, col).toDoubleOrNull() ?: 0.0
                        sum += value
                    }
                }
                return sum.toInt().toString()
            }
            return "0"
        } catch (e: Exception) {
            return "0"
        }
    }
    
    /**
     * 将Excel列字母转换为数字
     */
    private fun colLetterToNumber(letters: String): Int {
        var result = 0
        for (char in letters) {
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1 // 转换为0基索引
    }
    
    /**
     * 计算数学表达式
     */
    private fun evaluateMathExpression(expression: String): String {
        try {
            // 如果表达式只是纯数字，直接返回
            if (expression.matches(Regex("^\\d+$"))) {
                return expression
            }
            
            // 清理表达式，保留数字、运算符和括号
            val cleanExpression = expression.replace(Regex("[^0-9+\\-*/.()]"), "")
            if (cleanExpression.isBlank()) return expression
            
            // 使用简单的表达式解析器
            return evaluateSimpleExpression(cleanExpression)
        } catch (e: Exception) {
            return expression
        }
    }
    
    /**
     * 简单的表达式计算器
     */
    private fun evaluateSimpleExpression(expression: String): String {
        try {
            // 处理括号
            var expr = expression
            while (expr.contains("(") && expr.contains(")")) {
                val start = expr.lastIndexOf("(")
                val end = expr.indexOf(")", start)
                if (start >= 0 && end > start) {
                    val innerExpr = expr.substring(start + 1, end)
                    val result = calculateSimpleMath(innerExpr)
                    expr = expr.substring(0, start) + result + expr.substring(end + 1)
                } else {
                    break
                }
            }
            
            return calculateSimpleMath(expr)
        } catch (e: Exception) {
            return expression
        }
    }
    
    /**
     * 计算简单的数学表达式（无括号）
     */
    private fun calculateSimpleMath(expression: String): String {
        try {
            // 使用更简单的方法：直接计算表达式
            return evaluateExpression(expression)
        } catch (e: Exception) {
            return expression
        }
    }
    
    /**
     * 简单的表达式计算
     */
    private fun evaluateExpression(expr: String): String {
        try {
            // 处理简单的加减乘除运算
            var result = expr.replace(" ", "")
            
            // 处理乘除运算
            val multiplyDivideRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*([*/])\\s*(\\d+(?:\\.\\d+)?)")
            while (multiplyDivideRegex.containsMatchIn(result)) {
                result = multiplyDivideRegex.replace(result) { matchResult ->
                    val left = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
                    val operator = matchResult.groupValues[2]
                    val right = matchResult.groupValues[3].toDoubleOrNull() ?: 0.0
                    
                    val calculated = when (operator) {
                        "*" -> left * right
                        "/" -> if (right != 0.0) left / right else 0.0
                        else -> 0.0
                    }
                    calculated.toString()
                }
            }
            
            // 处理加减运算
            val addSubtractRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*([+-])\\s*(\\d+(?:\\.\\d+)?)")
            while (addSubtractRegex.containsMatchIn(result)) {
                result = addSubtractRegex.replace(result) { matchResult ->
                    val left = matchResult.groupValues[1].toDoubleOrNull() ?: 0.0
                    val operator = matchResult.groupValues[2]
                    val right = matchResult.groupValues[3].toDoubleOrNull() ?: 0.0
                    
                    val calculated = when (operator) {
                        "+" -> left + right
                        "-" -> left - right
                        else -> 0.0
                    }
                    calculated.toString()
                }
            }
            
            return result.toDoubleOrNull()?.toInt()?.toString() ?: expr
        } catch (e: Exception) {
            return expr
        }
    }
    
    /**
     * 提取字符串中的最后一个数字
     */
    private fun extractLastNumber(str: String): Double {
        val regex = Regex("\\d+\\.?\\d*")
        val matches = regex.findAll(str)
        return matches.lastOrNull()?.value?.toDoubleOrNull() ?: 0.0
    }
    
    /**
     * 提取字符串中的第一个数字
     */
    private fun extractFirstNumber(str: String): Double {
        val regex = Regex("\\d+\\.?\\d*")
        return regex.find(str)?.value?.toDoubleOrNull() ?: 0.0
    }
    
    /**
     * 通用的下载当前预览方法
     */
    fun handleDownloadCurrentPreview(parentComponent: JComponent) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        val lastDir: File? = lastSelectedDirectory
        if (lastDir != null && lastDir.exists()) {
            fileChooser.currentDirectory = lastDir
        }
        
        // 根据当前预览类型设置默认文件名
        val defaultFileName = when {
            orderRemovalResult != null -> "移除已完成订单_${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx"
            smartMergeResult != null -> {
                val smartMerger = io.github.kotlin.fibonacci.core.SmartTableMerger()
                smartMerger.generateFileName()
            }
            mergedTable != null -> "合并结果.xlsx"
            else -> "当前预览_${java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))}.xlsx"
        }
        fileChooser.selectedFile = File(defaultFileName)
        
        val result = fileChooser.showSaveDialog(parentComponent)
        if (result == JFileChooser.APPROVE_OPTION) {
            lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val exporter = io.github.kotlin.fibonacci.excel.ExcelExporter()
                when {
                    orderRemovalResult != null -> {
                        // 导出订单移除结果
                        val filteredTable = orderRemovalResult!!.filteredTable
                        val mergedTableData = io.github.kotlin.fibonacci.model.MergedTableData(
                            sourceTables = listOf(filteredTable),
                            headers = filteredTable.headers,
                            rows = filteredTable.rows,
                            formulas = filteredTable.formulas
                        )
                        exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                    }
                    smartMergeResult != null -> {
                        // 导出智能合并结果
                        val updatedTable = smartMergeResult!!.updatedTable
                        val mergedTableData = io.github.kotlin.fibonacci.model.MergedTableData(
                            sourceTables = listOf(smartMergeResult!!.originalTable, updatedTable),
                            headers = updatedTable.headers,
                            rows = updatedTable.rows,
                            formulas = updatedTable.formulas
                        )
                        exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                    }
                    mergedTable != null -> {
                        // 导出普通合并结果
                        exporter.exportToExcel(mergedTable!!, fileChooser.selectedFile)
                    }
                    else -> {
                        javax.swing.JOptionPane.showMessageDialog(parentComponent, "没有可导出的数据", "提示", JOptionPane.WARNING_MESSAGE)
                        return
                    }
                }
                javax.swing.JOptionPane.showMessageDialog(parentComponent, "下载成功！\n文件保存为: ${fileChooser.selectedFile.name}", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                javax.swing.JOptionPane.showMessageDialog(parentComponent, "下载失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
}
