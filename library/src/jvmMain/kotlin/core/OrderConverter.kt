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
        
        tableData.rows.forEachIndexed { rowIndex, row ->
            try {
                val order = convertRowToOrder(row, tableData.headers, rowIndex)
                if (order != null) {
                    orders.add(order)
                }
            } catch (e: Exception) {
                // 跳过无法转换的行，记录错误但不中断处理
            }
        }
        
        return orders
    }
    
    /**
     * 将单行数据转换为生产订单
     */
    private fun convertRowToOrder(row: List<String>, headers: List<String>, rowIndex: Int): ProductionOrder? {
        if (row.isEmpty()) return null
        
        // 根据列名映射获取数据
        val id = getValueByHeader(row, headers, "序号") ?: row[0].takeIf { it.isNotBlank() } ?: return null
        val companyModel = getValueByHeader(row, headers, "公司型号") ?: ""
        val customerModel = getValueByHeader(row, headers, "客户型号") ?: ""
        val customerName = getValueByHeader(row, headers, "客户名称") ?: ""
        val plannedDeliveryDate = parseDate(getValueByHeader(row, headers, "计划发货时间"))
        val plannedQuantity = getValueByHeader(row, headers, "计划发货数量")?.toIntOrNull() ?: 0
        val quantity = getValueByHeader(row, headers, "数量（支）")?.toIntOrNull() ?: 0
        val segments = getValueByHeader(row, headers, "段数")?.toIntOrNull() ?: 1
        val deliveryPeriod = parseDate(getValueByHeader(row, headers, "交付期"))
        val innerDiameter = getValueByHeader(row, headers, "内径")?.toDoubleOrNull() ?: 0.0
        val outerDiameter = getValueByHeader(row, headers, "外径")?.toDoubleOrNull() ?: 0.0
        val dailyProduction = getValueByHeader(row, headers, "日产量")?.toIntOrNull() ?: 0
        val productionDays = getValueByHeader(row, headers, "生产天数")?.toDoubleOrNull() ?: 0.0
        val remainingDays = getValueByHeader(row, headers, "剩余天数")?.toDoubleOrNull() ?: 0.0
        val shippedQuantity = getValueByHeader(row, headers, "已发货数")?.toIntOrNull() ?: 0
        val unshippedQuantity = getValueByHeader(row, headers, "未发数量")?.toIntOrNull() ?: 0
        val machine = getValueByHeader(row, headers, "机台") ?: ""
        val pipeStatus = getValueByHeader(row, headers, "管子情况") ?: ""
        val pipeQuantity = getValueByHeader(row, headers, "管/棒数量")?.toIntOrNull() ?: 0
        val pipeArrivalDate = parseDate(getValueByHeader(row, headers, "采购回馈（-1管子时间）"))
        val injectionCompleted = getValueByHeader(row, headers, "注塑完成")?.toIntOrNull()
        
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
            status = status
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
     */
    private fun determineOrderStatus(plannedQuantity: Int, shippedQuantity: Int, unshippedQuantity: Int): OrderStatus {
        return when {
            plannedQuantity > 0 && plannedQuantity == shippedQuantity -> OrderStatus.COMPLETED
            shippedQuantity > 0 -> OrderStatus.IN_PRODUCTION
            unshippedQuantity > 0 -> OrderStatus.PENDING
            else -> OrderStatus.PENDING
        }
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
