package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 订单筛选器
 * 负责从输出表中筛选未完成订单，并根据发货计划表调整优先级
 */
class OrderFilter {
    
    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    )
    
    /**
     * 从输出表筛选未完成订单
     */
    fun filterIncompleteOrders(outputTable: TableData): List<ProductionOrder> {
        val converter = OrderConverter()
        val allOrders = converter.convertToProductionOrders(outputTable)
        
        return allOrders.filter { order ->
            // 未完成条件：计划发货数量 != 已发货数量 且 管子情况 != "已完成"
            val isNotCompleted = order.plannedQuantity != order.shippedQuantity
            val pipeNotCompleted = order.pipeStatus != "已完成"
            val hasUnshippedQuantity = order.unshippedQuantity > 0
            
            isNotCompleted && pipeNotCompleted && hasUnshippedQuantity
        }
    }
    
    /**
     * 根据发货计划表调整优先级
     */
    fun adjustPriorityByShippingPlan(orders: List<ProductionOrder>, 
                                   shippingPlanTable: TableData): List<ProductionOrder> {
        val shippingPriorities = extractShippingPriorities(shippingPlanTable)
        
        return orders.map { order ->
            val priority = shippingPriorities.find { it.orderId == order.id }
            if (priority != null) {
                order.copy(priority = OrderPriority.URGENT) // 发货计划表中的订单设为紧急
            } else {
                order
            }
        }
    }
    
    /**
     * 提取发货优先级信息
     */
    private fun extractShippingPriorities(shippingPlanTable: TableData): List<ShippingPriority> {
        val priorities = mutableListOf<ShippingPriority>()
        
        shippingPlanTable.rows.forEach { row ->
            val orderId = getValueByHeader(row, shippingPlanTable.headers, "序号") ?: return@forEach
            val contractNumber = getValueByHeader(row, shippingPlanTable.headers, "客户合同号") ?: ""
            val deliveryDate = parseDate(getValueByHeader(row, shippingPlanTable.headers, "交货时间"))
            
            if (deliveryDate != null) {
                priorities.add(ShippingPriority(
                    orderId = orderId,
                    customerContractNumber = contractNumber,
                    deliveryDate = deliveryDate,
                    priority = 1, // 发货计划表中的订单优先级最高
                    reason = "发货计划表优先"
                ))
            }
        }
        
        return priorities
    }
    
    /**
     * 根据条件筛选订单
     */
    fun filterOrdersByCondition(orders: List<ProductionOrder>, 
                               condition: FilterCondition): List<ProductionOrder> {
        return orders.filter { order ->
            when (condition.type) {
                FilterType.COMPLETION_STATUS -> {
                    when (condition.value) {
                        "未完成" -> !order.isCompleted && order.unshippedQuantity > 0
                        "已完成" -> order.isCompleted
                        "生产中" -> order.status == OrderStatus.IN_PRODUCTION
                        else -> true
                    }
                }
                FilterType.PRIORITY -> {
                    when (condition.value) {
                        "紧急" -> order.priority == OrderPriority.URGENT
                        "高" -> order.priority == OrderPriority.HIGH
                        "中" -> order.priority == OrderPriority.MEDIUM
                        "低" -> order.priority == OrderPriority.LOW
                        else -> true
                    }
                }
                FilterType.DELIVERY_DATE -> {
                    val targetDate = parseDate(condition.value)
                    if (targetDate != null) {
                        order.plannedDeliveryDate?.isBefore(targetDate) ?: false
                    } else {
                        true
                    }
                }
                FilterType.MACHINE -> {
                    order.machine == condition.value || condition.value == "全部"
                }
                FilterType.DIAMETER_RANGE -> {
                    val range = parseDiameterRange(condition.value)
                    if (range != null) {
                        order.outerDiameter in range.first..range.second
                    } else {
                        true
                    }
                }
                FilterType.QUANTITY_RANGE -> {
                    val range = parseQuantityRange(condition.value)
                    if (range != null) {
                        order.quantity in range.first..range.second
                    } else {
                        true
                    }
                }
                else -> true
            }
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
     * 解析直径范围
     */
    private fun parseDiameterRange(rangeStr: String): Pair<Double, Double>? {
        if (rangeStr.contains("-")) {
            val parts = rangeStr.split("-")
            if (parts.size == 2) {
                val min = parts[0].toDoubleOrNull()
                val max = parts[1].toDoubleOrNull()
                if (min != null && max != null) {
                    return Pair(min, max)
                }
            }
        }
        return null
    }
    
    /**
     * 解析数量范围
     */
    private fun parseQuantityRange(rangeStr: String): Pair<Int, Int>? {
        if (rangeStr.contains("-")) {
            val parts = rangeStr.split("-")
            if (parts.size == 2) {
                val min = parts[0].toIntOrNull()
                val max = parts[1].toIntOrNull()
                if (min != null && max != null) {
                    return Pair(min, max)
                }
            }
        }
        return null
    }
}

/**
 * 筛选条件
 */
data class FilterCondition(
    val type: FilterType,
    val value: String,
    val operator: FilterOperator = FilterOperator.EQUALS
)

/**
 * 筛选类型
 */
enum class FilterType {
    COMPLETION_STATUS,    // 完成状态
    PRIORITY,            // 优先级
    DELIVERY_DATE,       // 交付日期
    MACHINE,             // 机台
    DIAMETER_RANGE,      // 直径范围
    QUANTITY_RANGE       // 数量范围
}

/**
 * 筛选操作符
 */
enum class FilterOperator {
    EQUALS,              // 等于
    NOT_EQUALS,          // 不等于
    GREATER_THAN,        // 大于
    LESS_THAN,           // 小于
    CONTAINS,            // 包含
    NOT_CONTAINS         // 不包含
}
