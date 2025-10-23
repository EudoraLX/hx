package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import java.awt.*

/**
 * 增强的预览管理器
 * 支持多标签页预览不同类型的数据
 */
class EnhancedPreviewManager {
    
    private val machineAssignmentEngine = MachineAssignmentEngine()
    var machineRules = mutableListOf<MachineRule>()
    
    /**
     * 加载默认机台规则
     */
    private fun loadDefaultMachineRules() {
        if (machineRules.isEmpty()) {
            machineRules.addAll(machineAssignmentEngine.createDefaultMachineRules())
        }
    }
    
    /**
     * 更新机台配置预览
     */
    fun updateMachineConfigPreview(table: JTable) {
        // 确保机台规则已加载
        loadDefaultMachineRules()
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = 5
            override fun getRowCount(): Int = machineRules.size
            override fun getValueAt(row: Int, col: Int): Any {
                val rule = machineRules[row]
                return when (col) {
                    0 -> rule.machineId
                    1 -> rule.moldId
                    2 -> rule.pipeSpecs.joinToString(", ") { 
                        when {
                            it.innerDiameter == 0.0 -> "Ø ${it.outerDiameter}${if (it.isLarge) "(大)" else ""}"
                            else -> "${it.innerDiameter}/${it.outerDiameter}${if (it.isLarge) "(大)" else ""}"
                        }
                    }
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
        
        table.model = model
        adjustColumnWidths(table)
    }
    
    /**
     * 更新原始数据预览
     */
    fun updateOriginalDataPreview(table: JTable, tableData: TableData?) {
        if (tableData == null) {
            table.model = javax.swing.table.DefaultTableModel()
            return
        }
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = tableData.columnCount
            override fun getRowCount(): Int = tableData.rowCount
            override fun getValueAt(row: Int, col: Int): Any {
                val formula = tableData.getFormula(row, col)
                if (formula != null && formula.isNotBlank()) {
                    try {
                        return calculateFormula(formula, tableData, row, col)
                    } catch (e: Exception) {
                        return formula
                    }
                } else {
                    return tableData.getValue(row, col)
                }
            }
            override fun getColumnName(col: Int): String = tableData.headers[col]
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        table.model = model
        adjustColumnWidths(table)
    }
    
    /**
     * 更新筛选结果预览
     * 保持原表顺序，不进行排序
     */
    fun updateFilteredDataPreview(table: JTable, orders: List<ProductionOrder>, excludedOrders: List<ProductionOrder> = emptyList()) {
        // 如果没有显式传入排除订单，根据排除条件识别排除的订单
        val actualExcludedOrders = if (excludedOrders.isEmpty()) {
            orders.filter { order ->
                // 不参与排产的条件：
                // 1. 备注包含"已完成"或"改制"
                // 2. 外径为0
                // 3. 注射完成 > 未发货数
                val notes = order.notes ?: ""
                val hasExcludedNotes = notes.contains("已完成") || notes.contains("改制")
                val hasZeroOuterDiameter = order.outerDiameter <= 0
                val injectionCompleted = order.injectionCompleted ?: 0
                val injectionExceedsUnshipped = injectionCompleted > order.unshippedQuantity
                
                hasExcludedNotes || hasZeroOuterDiameter || injectionExceedsUnshipped
            }
        } else {
            excludedOrders
        }
        
        // 保持原表顺序：只显示传入的订单，不合并排除的订单
        // 排除的订单会在绿色标注中显示
        val allOrders = orders
        
        if (allOrders.isEmpty()) {
            table.model = javax.swing.table.DefaultTableModel()
            return
        }
        
        val headers = listOf(
            "序号", "公司型号", "客户名称", "计划发货时间", "计划发货数量", 
            "数量（支）", "交付期", "内径", "外径", "管子数量", "管子到货", "日产量", "生产天数", 
            "剩余天数", "已发货数", "未发数量", "注射完成", "机台", "管子情况", 
            "优先级", "状态", "备注", "排产状态"
        )
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = headers.size
            override fun getRowCount(): Int = allOrders.size
            override fun getValueAt(row: Int, col: Int): Any {
                val order = allOrders[row]
                val isExcluded = actualExcludedOrders.contains(order)
                
                return when (col) {
                    0 -> order.id
                    1 -> order.companyModel
                    2 -> order.customerName
                    3 -> order.plannedDeliveryDate?.toString() ?: ""
                    4 -> order.plannedQuantity.toString()
                    5 -> order.quantity.toString()
                    6 -> order.deliveryPeriod?.toString() ?: ""
                    7 -> order.innerDiameter.toString()
                    8 -> order.outerDiameter.toString()
                    9 -> order.pipeQuantity.toString()
                    10 -> order.pipeArrivalDate?.toString() ?: "已到货"
                    11 -> order.dailyProduction.toString()
                    12 -> order.productionDays.toString()
                    13 -> order.remainingDays.toString()
                    14 -> order.shippedQuantity.toString()
                    15 -> order.unshippedQuantity.toString()
                    16 -> order.injectionCompleted?.toString() ?: "0"
                    17 -> order.machine
                    18 -> order.pipeStatus
                    19 -> order.priority.name
                    20 -> order.status.name
                    21 -> order.notes
                    22 -> if (isExcluded) "不参与排产（绿色标注）" else "参与排产"
                    else -> ""
                }
            }
            override fun getColumnName(col: Int): String = headers[col]
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        table.model = model
        
        // 设置行颜色：不参与排产的订单用绿色背景
        table.setDefaultRenderer(Object::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, 
                row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                
                val order = allOrders[row]
                val isExcluded = actualExcludedOrders.contains(order)
                
                if (isExcluded) {
                    // 不参与排产的订单用绿色背景
                    background = Color(144, 238, 144) // 浅绿色
                    foreground = Color.BLACK
                } else {
                    // 参与排产的订单用默认颜色
                    background = if (isSelected) table?.selectionBackground else table?.background
                    foreground = if (isSelected) table?.selectionForeground else table?.foreground
                }
                
                return component
            }
        })
        
        adjustColumnWidths(table)
    }
    
    /**
     * 更新优先级调整预览
     */
    fun updatePriorityPreview(table: JTable, orders: List<ProductionOrder>, shippingPlanTable: TableData?) {
        if (orders.isEmpty()) {
            table.model = javax.swing.table.DefaultTableModel()
            return
        }
        
        val headers = listOf(
            "序号", "公司型号", "客户名称", "原优先级", "调整后优先级", "调整原因"
        )
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = headers.size
            override fun getRowCount(): Int = orders.size
            override fun getValueAt(row: Int, col: Int): Any {
                val order = orders[row]
                val isInShippingPlan = shippingPlanTable?.let { isOrderInShippingPlan(order, it) } ?: false
                
                return when (col) {
                    0 -> order.id
                    1 -> order.companyModel
                    2 -> order.customerName
                    3 -> order.priority.name
                    4 -> if (isInShippingPlan) "紧急" else order.priority.name
                    5 -> if (isInShippingPlan) "发货计划表优先" else "无调整"
                    else -> ""
                }
            }
            override fun getColumnName(col: Int): String = headers[col]
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        table.model = model
        adjustColumnWidths(table)
    }
    
    /**
     * 检查订单是否在发货计划表中
     */
    private fun isOrderInShippingPlan(order: ProductionOrder, shippingPlanTable: TableData): Boolean {
        return shippingPlanTable.rows.any { row ->
            val companyModel = getValueByHeader(row, shippingPlanTable.headers, "公司型号")
            val customerModel = getValueByHeader(row, shippingPlanTable.headers, "客户型号")
            
            (companyModel != null && companyModel == order.companyModel) ||
            (customerModel != null && customerModel == order.customerModel)
        }
    }
    
    /**
     * 根据列名获取值
     */
    private fun getValueByHeader(row: List<String>, headers: List<String>, headerName: String): String? {
        val columnIndex = headers.indexOf(headerName)
        return if (columnIndex >= 0 && columnIndex < row.size) {
            row[columnIndex].takeIf { it.isNotBlank() }
        } else null
    }
    
    /**
     * 更新排产计划表预览
     */
    fun updateSchedulingPlanPreview(table: JTable, schedulingPlanTable: TableData?) {
        if (schedulingPlanTable == null) {
            table.model = javax.swing.table.DefaultTableModel()
            return
        }
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = schedulingPlanTable.columnCount
            override fun getRowCount(): Int = schedulingPlanTable.rowCount
            override fun getValueAt(row: Int, col: Int): Any {
                val cellValue = schedulingPlanTable.getValue(row, col)
                val formula = schedulingPlanTable.getFormula(row, col)
                
                return if (formula != null && formula.isNotBlank()) {
                    calculateFormula(formula, schedulingPlanTable, row, col)
                } else {
                    cellValue
                }
            }
            override fun getColumnName(col: Int): String = schedulingPlanTable.headers[col]
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        table.model = model
        adjustColumnWidths(table)
    }
    
    /**
     * 更新排产结果预览
     */
    fun updateSchedulingResultPreview(table: JTable, schedulingResult: SchedulingResult?) {
        if (schedulingResult == null) {
            table.model = javax.swing.table.DefaultTableModel()
            return
        }
        
        val headers = listOf(
            "序号", "公司型号", "客户名称", "机台", "模具", "计划开始时间", "计划完成时间", 
            "管子数", "段数", "总段数", "管子库存", "日产量", "生产天数", "优先级", "状态", "组合信息"
        )
        
        val model = object : javax.swing.table.DefaultTableModel() {
            override fun getColumnCount(): Int = headers.size
            override fun getRowCount(): Int = schedulingResult.orders.size
            override fun getValueAt(row: Int, col: Int): Any {
                val order = schedulingResult.orders[row]
                val totalSegments = order.quantity * order.segments
                
                return when (col) {
                    0 -> order.id
                    1 -> order.companyModel
                    2 -> order.customerName
                    3 -> order.machine
                    4 -> getMoldForOrder(order, schedulingResult.machineSchedule)
                    5 -> order.startDate?.toString() ?: ""
                    6 -> order.endDate?.toString() ?: ""
                    7 -> order.quantity.toString()
                    8 -> order.segments.toString()
                    9 -> totalSegments.toString()
                    10 -> order.pipeQuantity.toString()
                    11 -> order.dailyProduction.toString()
                    12 -> String.format("%.1f", order.productionDays)
                    13 -> order.priority.name
                    14 -> order.status.name
                    15 -> if (order.notes.contains("组合订单")) order.notes else ""
                    else -> ""
                }
            }
            override fun getColumnName(col: Int): String = headers[col]
            override fun isCellEditable(row: Int, col: Int): Boolean = false
        }
        
        table.model = model
        adjustColumnWidths(table)
    }
    
    /**
     * 获取订单对应的模具
     */
    private fun getMoldForOrder(order: ProductionOrder, machineSchedule: Map<String, List<ProductionOrder>>): String {
        // 根据机台配置获取模具名称
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val machineRule = machineRules.find { it.machineId == order.machine }
        
        return if (machineRule != null) {
            machineRule.moldId
        } else {
            // 如果没有找到对应的机台规则，使用默认格式
            val machineOrders = machineSchedule[order.machine] ?: return ""
            val orderIndex = machineOrders.indexOf(order)
            if (orderIndex >= 0) "模具${orderIndex + 1}" else ""
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
     * 计算公式
     */
    private fun calculateFormula(formula: String, table: TableData, row: Int, col: Int): String {
        try {
            // 处理SUM函数，如 SUM(W112:Y112)
            if (formula.startsWith("SUM(") && formula.endsWith(")")) {
                val sumRange = formula.substring(4, formula.length - 1) // 移除SUM(和)
                return calculateSumRange(sumRange, table, row)
            }
            
            // 处理简单的单元格引用，如 T2, T3, T4 等
            if (formula.matches(Regex("^[A-Z]+\\d+$"))) {
                return calculateSimpleCellReference(formula, table)
            }
            
            // 处理Excel单元格引用公式，如 AD2+V2-T2
            val cellRefPattern = Regex("[A-Z]+\\d+")
            val cellRefs = cellRefPattern.findAll(formula)
            
            var processedExpression = formula
            for (cellRef in cellRefs) {
                val ref = cellRef.value
                val colLetter = ref.filter { it.isLetter() }
                val rowNum = ref.filter { it.isDigit() }.toInt() - 1 // Excel行号从1开始，数组从0开始
                
                // 将列字母转换为数字
                val colNum = colLetterToNumber(colLetter)
                
                // Excel行号从1开始，第1行是标题，第2行开始是数据
                // 所以Excel第2行对应数组索引0，Excel第3行对应数组索引1
                val arrayRowIndex = rowNum - 1
                
                if (colNum >= 0 && arrayRowIndex >= 0 && arrayRowIndex < table.rowCount && colNum < table.columnCount) {
                    val cellValue = table.getValue(arrayRowIndex, colNum)
                    val numericValue = cellValue.toDoubleOrNull() ?: 0.0 // 空值按0计算
                    processedExpression = processedExpression.replace(ref, numericValue.toString())
                }
            }
            
            // 尝试计算数学表达式
            if (processedExpression.matches(Regex(".*[+\\-*/].*"))) {
                return evaluateMathExpression(processedExpression)
            } else if (processedExpression.matches(Regex("^\\d+$"))) {
                return processedExpression
            }
            
            return formula
        } catch (e: Exception) {
            return formula
        }
    }
    
    /**
     * 计算简单单元格引用
     */
    private fun calculateSimpleCellReference(cellRef: String, table: TableData): String {
        try {
            val colLetter = cellRef.filter { it.isLetter() }
            val rowNum = cellRef.filter { it.isDigit() }.toInt() - 1 // Excel行号从1开始，数组从0开始
            
            // 将列字母转换为数字
            val colNum = colLetterToNumber(colLetter)
            
            // Excel行号从1开始，第1行是标题，第2行开始是数据
            // 所以Excel第2行对应数组索引0，Excel第3行对应数组索引1
            val arrayRowIndex = rowNum - 1
            
            if (colNum >= 0 && arrayRowIndex >= 0 && arrayRowIndex < table.rowCount && colNum < table.columnCount) {
                val cellValue = table.getValue(arrayRowIndex, colNum)
                val numericValue = cellValue.toDoubleOrNull()
                
                return if (numericValue != null) {
                    numericValue.toInt().toString()
                } else {
                    // 如果引用的单元格也是公式，尝试递归计算
                    if (cellValue.matches(Regex("^[A-Z]+\\d+$"))) {
                        calculateSimpleCellReference(cellValue, table)
                    } else {
                        "0"
                    }
                }
            }
            
            return "0"
        } catch (e: Exception) {
            return "0"
        }
    }
    
    /**
     * 计算SUM函数范围
     */
    private fun calculateSumRange(range: String, table: TableData, row: Int): String {
        try {
            // 处理范围，如 W112:Y112
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
                val excelRowNum = startCell.filter { it.isDigit() }.toInt() // Excel行号（从1开始）
                
                // Excel行号从1开始，第1行是标题，第2行开始是数据
                // 所以Excel第2行对应数组索引0，Excel第3行对应数组索引1
                val arrayRowIndex = excelRowNum - 2
                
                // 转换为列索引
                val startCol = colLetterToNumber(startColLetters)
                val endCol = colLetterToNumber(endColLetters)
                
                var sum = 0.0
                // 确保列索引有效
                if (startCol >= 0 && endCol >= 0 && startCol <= endCol && arrayRowIndex >= 0 && arrayRowIndex < table.rowCount) {
                    for (col in startCol..endCol) {
                        if (col < table.columnCount) {
                            val value = table.getValue(arrayRowIndex, col)
                            val numericValue = value.toDoubleOrNull() ?: 0.0 // 空值按0计算
                            sum += numericValue
                        }
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
            var result = expression.replace(" ", "")
            
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
            
            return result.toDoubleOrNull()?.toInt()?.toString() ?: expression
        } catch (e: Exception) {
            return expression
        }
    }
}
