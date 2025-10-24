package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 订单数据转换器
 * 将Excel表格数据转换为生产订单对象
 */
class OrderConverter {
    
    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    )
    
    /**
     * 将表格数据转换为生产订单列表
     */
    fun convertToProductionOrders(tableData: TableData): List<ProductionOrder> {
        val orders = mutableListOf<ProductionOrder>()
        val errors = mutableListOf<String>()
        
        tableData.rows.forEachIndexed { rowIndex, row ->
            try {
                val order = convertRowToOrder(row, tableData.headers, rowIndex)
                if (order != null) {
                    // 备注保持原样，不添加任何标记
                    orders.add(order)
                } else {
                    errors.add("第${rowIndex + 1}行：无法转换订单数据")
                }
            } catch (e: Exception) {
                // 记录转换错误，但不中断处理
                errors.add("第${rowIndex + 1}行：转换失败 - ${e.message}")
            }
        }
        
        // 如果有错误，打印到控制台（用于调试）
        if (errors.isNotEmpty()) {
            println("数据转换警告：")
            errors.forEach { println("  $it") }
        }
        
        return orders
    }
    
    /**
     * 检查订单是否应该被排除（不参与排产）
     */
    private fun isOrderExcluded(order: ProductionOrder): Boolean {
        // 不参与排产的条件：
        // 1. 备注包含"已完成"或"改制"
        // 2. 外径为0
        // 3. 注塑完成 > 未发货数
        // 4. 日产量为空（新增条件）
        val notes = order.notes ?: ""
        val hasExcludedNotes = notes.contains("已完成") || notes.contains("改制")
        
        val hasZeroOuterDiameter = order.outerDiameter <= 0
        
        val injectionCompleted = order.injectionCompleted ?: 0
        val injectionExceedsUnshipped = injectionCompleted > order.unshippedQuantity
        
        val hasNoDailyProduction = order.dailyProduction <= 0
        
        return hasExcludedNotes || hasZeroOuterDiameter || injectionExceedsUnshipped || hasNoDailyProduction
    }
    
    /**
     * 将单行数据转换为生产订单
     */
    private fun convertRowToOrder(row: List<String>, headers: List<String>, rowIndex: Int): ProductionOrder? {
        if (row.isEmpty()) return null
        
        // 检查行数据完整性
        if (row.size < headers.size) {
            println("警告：第${rowIndex + 1}行数据不完整，行数据列数：${row.size}，表头列数：${headers.size}")
        }
        
        // 根据列名映射获取数据
        val id = getValueByHeader(row, headers, "序号") ?: 
                 row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: 
                 "ROW_${rowIndex + 1}" // 如果都没有，使用行号作为ID
        val companyModel = getValueByHeader(row, headers, "公司型号") ?: ""
        val customerModel = getValueByHeader(row, headers, "客户型号") ?: ""
        val customerName = getValueByHeader(row, headers, "客户名称") ?: ""
        val plannedDeliveryDate = parseDate(getValueByHeader(row, headers, "计划发货时间"))
        val plannedQuantity = getValueByHeader(row, headers, "计划发货数量")?.toIntOrNull() ?: 0
        val quantity = getValueByHeader(row, headers, "数量（支）")?.toIntOrNull() ?: 0
        val segments = getValueByHeader(row, headers, "段数")?.toIntOrNull() ?: 1
        val deliveryPeriod = parseDate(getValueByHeader(row, headers, "交付期"))
        val innerDiameter = parseDiameterValue(getValueByHeader(row, headers, "内径"))
        val outerDiameter = parseDiameterValue(getValueByHeader(row, headers, "外径"))
        val dailyProduction = getValueByHeader(row, headers, "日产量")?.toIntOrNull() ?: 0
        val productionDays = getValueByHeader(row, headers, "生产天数")?.toDoubleOrNull() ?: 0.0
        val remainingDays = getValueByHeader(row, headers, "剩余天数")?.toDoubleOrNull() ?: 0.0
        val shippedQuantity = getValueByHeader(row, headers, "已发货数")?.toIntOrNull() ?: 0
        val unshippedQuantity = getValueByHeader(row, headers, "未发数量")?.toIntOrNull() ?: 0
        val machine = getValueByHeader(row, headers, "机台") ?: ""
        val pipeStatus = getValueByHeader(row, headers, "管子情况") ?: ""
        val pipeQuantity = getValueByHeader(row, headers, "管/棒数量")?.toIntOrNull() ?: 0
        val pipeArrivalDate = parseDate(getValueByHeader(row, headers, "采购回馈（-1管子时间）"))
        val injectionCompleted = getValueByHeader(row, headers, "注射完成")?.toIntOrNull()
        val notes = getValueByHeader(row, headers, "备注") ?: "" // 获取备注字段
        
        // 确定订单状态
        val status = determineOrderStatus(plannedQuantity, shippedQuantity, unshippedQuantity)
        val isCompleted = plannedQuantity > 0 && plannedQuantity == shippedQuantity && pipeStatus == "已完成"
        
        // 确定订单优先级
        val priority = determineOrderPriority(plannedDeliveryDate, remainingDays.toInt(), quantity)
        
        return ProductionOrder(
            id = id,
            companyModel = companyModel,
            customerModel = customerModel,
            customerName = customerName,
            plannedDeliveryDate = plannedDeliveryDate,
            plannedQuantity = plannedQuantity,
            quantity = quantity,
            segments = segments,
            deliveryPeriod = deliveryPeriod,
            innerDiameter = innerDiameter,
            outerDiameter = outerDiameter,
            dailyProduction = dailyProduction,
            productionDays = productionDays,
            remainingDays = remainingDays,
            shippedQuantity = shippedQuantity,
            unshippedQuantity = unshippedQuantity,
            machine = machine,
            pipeStatus = pipeStatus,
            pipeQuantity = pipeQuantity,
            pipeArrivalDate = pipeArrivalDate,
            injectionCompleted = injectionCompleted,
            isCompleted = isCompleted,
            priority = priority,
            status = status,
            notes = notes // 添加备注字段
        )
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
     * 解析内径外径值，支持多种格式
     * 支持格式：
     * 1. 纯数字：102
     * 2. 分数格式：102/195
     * 3. 多个规格：102/195 122/215
     * 4. 范围格式：102-195
     * 对于复杂格式，返回第一个有效值
     */
    private fun parseDiameterValue(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        
        val cleanValue = value.trim()
        
        // 如果是纯数字，直接返回
        val directNumber = cleanValue.toDoubleOrNull()
        if (directNumber != null) return directNumber
        
        // 处理多个规格的情况：102/195 122/215
        if (cleanValue.contains(" ") && cleanValue.contains("/")) {
            val parts = cleanValue.split(" ")
            for (part in parts) {
                val number = parseSingleDiameter(part.trim())
                if (number > 0) return number
            }
        }
        
        // 处理单个分数格式：102/195
        if (cleanValue.contains("/")) {
            return parseSingleDiameter(cleanValue)
        }
        
        // 处理范围格式：102-195
        if (cleanValue.contains("-")) {
            val parts = cleanValue.split("-")
            if (parts.size == 2) {
                val firstNumber = parts[0].trim().toDoubleOrNull()
                if (firstNumber != null && firstNumber > 0) return firstNumber
            }
        }
        
        // 如果都无法解析，返回0
        return 0.0
    }
    
    /**
     * 解析单个内径外径值
     */
    private fun parseSingleDiameter(value: String): Double {
        if (value.contains("/")) {
            // 处理分数格式：102/195，返回第一个数字（通常是内径）
            val parts = value.split("/")
            if (parts.size >= 1) {
                return parts[0].trim().toDoubleOrNull() ?: 0.0
            }
        }
        
        // 尝试直接解析为数字
        return value.toDoubleOrNull() ?: 0.0
    }
    
    /**
     * 解析日期字符串
     */
    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        
        for (formatter in dateFormatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter)
            } catch (e: DateTimeParseException) {
                // 继续尝试下一个格式
            }
        }
        
        return null
    }
    
    /**
     * 确定订单状态
     * 简化逻辑：所有筛选后的订单都是待排产状态
     */
    private fun determineOrderStatus(plannedQuantity: Int, shippedQuantity: Int, unshippedQuantity: Int): OrderStatus {
        // 所有筛选后的订单都是待排产状态
        // 不基于计划发货数和已发货数来判断状态
        return OrderStatus.PENDING
    }
    
    /**
     * 确定订单优先级
     */
    private fun determineOrderPriority(plannedDeliveryDate: LocalDate?, remainingDays: Int, quantity: Int): OrderPriority {
        val today = LocalDate.now()
        
        return when {
            // 紧急：3天内到期或剩余天数少于3天
            (plannedDeliveryDate != null && plannedDeliveryDate.isBefore(today.plusDays(3))) ||
            remainingDays in 1..3 -> OrderPriority.URGENT
            
            // 高优先级：7天内到期或剩余天数少于7天
            (plannedDeliveryDate != null && plannedDeliveryDate.isBefore(today.plusDays(7))) ||
            remainingDays in 4..7 -> OrderPriority.HIGH
            
            // 中优先级：大订单或14天内到期
            quantity > 1000 ||
            (plannedDeliveryDate != null && plannedDeliveryDate.isBefore(today.plusDays(14))) ||
            remainingDays in 8..14 -> OrderPriority.MEDIUM
            
            // 低优先级：其他情况
            else -> OrderPriority.LOW
        }
    }
    
    /**
     * 将生产订单转换回表格数据
     */
    fun convertToTableData(orders: List<ProductionOrder>, originalHeaders: List<String>): TableData {
        val rows = mutableListOf<List<String>>()
        val formulas = mutableListOf<List<String?>>()
        
        orders.forEach { order ->
            val row = mutableListOf<String>()
            val rowFormulas = mutableListOf<String?>()
            
            // 根据原始列顺序填充数据
            originalHeaders.forEach { header ->
                val value = when (header) {
                    "序号" -> order.id
                    "公司型号" -> order.companyModel
                    "客户型号" -> order.customerModel
                    "计划发货时间" -> order.plannedDeliveryDate?.toString() ?: ""
                    "计划发货数量" -> order.plannedQuantity.toString()
                    "数量（支）" -> order.quantity.toString()
                    "交付期" -> order.deliveryPeriod?.toString() ?: ""
                    "内径" -> order.innerDiameter.toString()
                    "外径" -> order.outerDiameter.toString()
                    "日产量" -> order.dailyProduction.toString()
                    "生产天数" -> order.productionDays.toString()
                    "剩余天数" -> order.remainingDays.toString()
                    "已发货数" -> order.shippedQuantity.toString()
                    "未发数量" -> order.unshippedQuantity.toString()
                    "机台" -> order.machine
                    "管子情况" -> order.pipeStatus
                    "注塑完成" -> order.injectionCompleted?.toString() ?: ""
                    "计划开始时间" -> order.startDate?.toString() ?: ""
                    "计划完成时间" -> order.endDate?.toString() ?: ""
                    "优先级" -> order.priority.name
                    "状态" -> order.status.name
                    "备注" -> order.notes
                    else -> ""
                }
                row.add(value)
                rowFormulas.add(null) // 暂时不处理公式
            }
            
            rows.add(row)
            formulas.add(rowFormulas)
        }
        
        return TableData(
            fileName = "排产结果",
            headers = originalHeaders,
            rows = rows,
            formulas = formulas
        )
    }
    
    /**
     * 创建默认机台列表
     */
    fun createDefaultMachines(): List<Machine> {
        return listOf(
            Machine("M001", "机台1", 100, 1.0),
            Machine("M002", "机台2", 120, 1.0),
            Machine("M003", "机台3", 80, 1.0),
            Machine("M004", "机台4", 150, 1.0),
            Machine("M005", "机台5", 90, 1.0)
        )
    }
}
