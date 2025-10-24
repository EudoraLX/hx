package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 排产流程管理器
 * 管理完整的排产流程：合并→筛选→排产→输出
 */
class SchedulingFlowManager {
    
    private val orderConverter = OrderConverter()
    private val orderFilter = OrderFilter()
    private val smartScheduler = SmartScheduler()
    private val machineAssignmentEngine = MachineAssignmentEngine()
    
    /**
     * 使用单个表格执行排产流程（简化版）
     */
    fun executeSchedulingFlowWithSingleTable(
        table: TableData,
        machineRules: List<MachineRule>,
        strategy: SchedulingStrategy = SchedulingStrategy.ORDER_FIRST
    ): SchedulingFlowResult {
        
        // 步骤1：直接使用导入的表格
        val mergedTable = table
        
        // 步骤2：筛选需要排产的订单
        println("🔍 开始筛选订单...")
        val filteredOrders = filterOrdersForScheduling(mergedTable)
        println("🔍 筛选后订单数: ${filteredOrders.size}")
        
        // 步骤2.5：获取不参与排产的订单（用于绿色标注）
        val excludedOrders = getExcludedOrders(mergedTable)
        println("🔍 排除订单数: ${excludedOrders.size}")
        
        // 步骤3：根据表格中的发货计划信息调整优先级
        println("🔍 优先级调整前订单数: ${filteredOrders.size}")
        val prioritizedOrders = adjustPriorityFromTable(filteredOrders, mergedTable)
        println("🔍 优先级调整后订单数: ${prioritizedOrders.size}")
        
        // 步骤4：智能排产（使用指定的策略）
        val schedulingResult = performSchedulingWithStrategy(prioritizedOrders, machineRules, strategy)
        
        // 步骤5：生成排产计划表
        val schedulingPlanTable = generateSchedulingPlanTable(mergedTable, schedulingResult)
        
        return SchedulingFlowResult(
            mergedTable = mergedTable,
            filteredOrders = prioritizedOrders,
            excludedOrders = excludedOrders, // 添加排除的订单
            schedulingResult = schedulingResult,
            schedulingPlanTable = schedulingPlanTable
        )
    }
    
    /**
     * 执行完整排产流程
     */
    fun executeSchedulingFlow(
        outputTable: TableData,
        shippingPlanTable: TableData,
        machineRules: List<MachineRule>
    ): SchedulingFlowResult {
        
        // 步骤1：合并输出表和发货计划表
        val mergedTable = mergeTablesByCompanyModel(outputTable, shippingPlanTable)
        
        // 步骤2：筛选需要排产的订单
        val filteredOrders = filterOrdersForScheduling(mergedTable)
        
        // 步骤2.5：获取不参与排产的订单（用于绿色标注）
        val excludedOrders = getExcludedOrders(mergedTable)
        
        // 步骤3：根据发货计划表调整优先级
        println("🔍 优先级调整前订单数: ${filteredOrders.size}")
        val prioritizedOrders = adjustPriorityByShippingPlan(filteredOrders, shippingPlanTable)
        println("🔍 优先级调整后订单数: ${prioritizedOrders.size}")
        
        // 步骤4：智能排产
        val schedulingResult = performScheduling(prioritizedOrders, machineRules)
        
        // 步骤5：生成排产计划表
        val schedulingPlanTable = generateSchedulingPlanTable(mergedTable, schedulingResult)
        
        return SchedulingFlowResult(
            mergedTable = mergedTable,
            filteredOrders = prioritizedOrders,
            excludedOrders = excludedOrders, // 添加排除的订单
            schedulingResult = schedulingResult,
            schedulingPlanTable = schedulingPlanTable
        )
    }
    
    /**
     * 步骤1：根据公司型号合并输出表和发货计划表
     */
    fun mergeTablesByCompanyModel(outputTable: TableData, shippingPlanTable: TableData): TableData {
        val mergedRows = mutableListOf<List<String>>()
        val mergedFormulas = mutableListOf<List<String?>>()
        
        // 创建发货计划表的公司型号映射
        val shippingPlanMap = createShippingPlanMap(shippingPlanTable)
        
        // 检查是否需要添加客户名称列
        val hasCustomerNameColumn = outputTable.headers.contains("客户名称")
        val newHeaders = if (!hasCustomerNameColumn) {
            outputTable.headers.toMutableList().apply {
                add("客户名称")
            }
        } else {
            outputTable.headers
        }
        
        // 处理输出表的每一行
        outputTable.rows.forEachIndexed { rowIndex, row ->
            val companyModel = getValueByHeader(row, outputTable.headers, "公司型号")
            val shippingInfo = shippingPlanMap[companyModel]
            
            val mergedRow = row.toMutableList()
            val mergedRowFormulas = mutableListOf<String?>()
            
            // 复制原表的公式
            repeat(outputTable.columnCount) { colIndex ->
                mergedRowFormulas.add(outputTable.getFormula(rowIndex, colIndex))
            }
            
            // 如果找到匹配的发货计划，添加发货计划信息
            if (shippingInfo != null) {
                // 添加或更新客户名称
                if (hasCustomerNameColumn) {
                    val customerNameIndex = outputTable.headers.indexOf("客户名称")
                    if (customerNameIndex >= 0 && customerNameIndex < mergedRow.size) {
                        mergedRow[customerNameIndex] = shippingInfo.customerName
                    }
                } else {
                    // 添加客户名称列
                    mergedRow.add(shippingInfo.customerName)
                    mergedRowFormulas.add(null)
                }
                
                // 在备注中添加发货计划信息
                val currentNotes = getValueByHeader(row, outputTable.headers, "备注") ?: ""
                val newNotes = if (currentNotes.isBlank()) {
                    "发货计划优先: ${shippingInfo.deliveryDate}"
                } else {
                    "$currentNotes; 发货计划优先: ${shippingInfo.deliveryDate}"
                }
                
                // 更新备注列
                val notesIndex = outputTable.headers.indexOf("备注")
                if (notesIndex >= 0 && notesIndex < mergedRow.size) {
                    mergedRow[notesIndex] = newNotes
                }
            } else {
                // 如果没有匹配的发货计划，但需要添加客户名称列，则添加空值
                if (!hasCustomerNameColumn) {
                    mergedRow.add("")
                    mergedRowFormulas.add(null)
                }
            }
            
            mergedRows.add(mergedRow)
            mergedFormulas.add(mergedRowFormulas)
        }
        
        return TableData(
            fileName = "合并后的输出表",
            headers = newHeaders,
            rows = mergedRows,
            formulas = mergedFormulas
        )
    }
    
    /**
     * 创建发货计划表的公司型号映射
     */
    private fun createShippingPlanMap(shippingPlanTable: TableData): Map<String, ShippingPlanInfo> {
        val map = mutableMapOf<String, ShippingPlanInfo>()
        
        shippingPlanTable.rows.forEach { row ->
            val companyModel = getValueByHeader(row, shippingPlanTable.headers, "公司型号")
            val customerModel = getValueByHeader(row, shippingPlanTable.headers, "客户型号")
            val customerName = getValueByHeader(row, shippingPlanTable.headers, "客户名称")
            val deliveryDate = parseDate(getValueByHeader(row, shippingPlanTable.headers, "交货时间"))
            val quantity = getValueByHeader(row, shippingPlanTable.headers, "数量（支）")?.toIntOrNull() ?: 0
            
            if (companyModel != null && deliveryDate != null) {
                map[companyModel] = ShippingPlanInfo(
                    companyModel = companyModel,
                    customerModel = customerModel ?: "",
                    customerName = customerName ?: "",
                    deliveryDate = deliveryDate,
                    quantity = quantity
                )
            }
        }
        
        return map
    }
    
    /**
     * 步骤2：统一筛选逻辑
     * 将所有订单分为两类：参与排产和不参与排产
     * 不参与排产的条件：
     * 1. 备注包含"已完成"或"改制"
     * 2. 外径为0
     * 3. 注射完成 >= 未发货数（无生产需求）
     */
    fun filterOrdersForScheduling(mergedTable: TableData): List<ProductionOrder> {
        val allOrders = orderConverter.convertToProductionOrders(mergedTable)
        
        // 保持原表顺序，只筛选不排序
        return allOrders.filter { order ->
            // 参与排产的条件：不满足任何排除条件
            !isOrderExcluded(order)
        }
    }
    
    /**
     * 获取不参与排产的订单（用于绿色标注）
     */
    fun getExcludedOrders(mergedTable: TableData): List<ProductionOrder> {
        val allOrders = orderConverter.convertToProductionOrders(mergedTable)
        
        return allOrders.filter { order ->
            // 不参与排产的条件
            isOrderExcluded(order)
        }
    }
    
    /**
     * 判断订单是否应该被排除（不参与排产）
     */
    private fun isOrderExcluded(order: ProductionOrder): Boolean {
        // 不参与排产的条件：
        // 1. 备注包含"已完成"或"改制"
        val notes = order.notes ?: ""
        val hasExcludedNotes = notes.contains("已完成") || notes.contains("改制")
        
        // 2. 外径为0
        val hasZeroOuterDiameter = order.outerDiameter <= 0
        
        // 3. 注射完成 >= 未发货数（无生产需求）
        val injectionCompleted = order.injectionCompleted ?: 0
        val noProductionNeed = injectionCompleted >= order.unshippedQuantity
        
        // 4. 日产量为空（新增条件）
        val hasNoDailyProduction = order.dailyProduction <= 0
        
        return hasExcludedNotes || hasZeroOuterDiameter || noProductionNeed || hasNoDailyProduction
    }
    
    /**
     * 步骤3：根据表格中的发货计划信息调整优先级
     * 只有真正在发货计划表中的订单才被标记为URGENT优先级
     */
    fun adjustPriorityFromTable(orders: List<ProductionOrder>, table: TableData): List<ProductionOrder> {
        return orders.map { order ->
            // 检查订单是否在发货计划中
            val isInShippingPlan = isOrderInShippingPlanFromTable(order, table)
            
            if (isInShippingPlan) {
                // 只有发货计划表中的订单才标记为URGENT
                order.copy(priority = OrderPriority.URGENT)
            } else {
                // 其他订单保持原有优先级或设为LOW
                order.copy(priority = OrderPriority.LOW)
            }
        }
    }
    
    /**
     * 检查订单是否在表格的发货计划中
     */
    private fun isOrderInShippingPlanFromTable(order: ProductionOrder, table: TableData): Boolean {
        // 检查是否有发货计划相关的列
        val hasShippingColumns = listOf("客户合同号", "合同号", "签订客户", "客户名称", "客户型号", "业务员", "交货时间")
            .any { column -> table.headers.contains(column) }
        
        if (!hasShippingColumns) {
            // 如果没有发货计划列，检查备注
            val notes = getValueByHeader(
                table.rows.find { row -> 
                    getValueByHeader(row, table.headers, "序号") == order.id 
                } ?: emptyList(), 
                table.headers, 
                "备注"
            ) ?: ""
            
            return notes.contains("发货计划") || notes.contains("优先") || notes.contains("紧急")
        }
        
        // 如果有发货计划列，检查该订单是否在发货计划中
        val orderRow = table.rows.find { row -> 
            getValueByHeader(row, table.headers, "序号") == order.id 
        }
        
        if (orderRow != null) {
            // 检查是否有发货计划相关的非空字段
            val hasCustomerContract = getValueByHeader(orderRow, table.headers, "客户合同号")?.isNotBlank() == true
            val hasContractNumber = getValueByHeader(orderRow, table.headers, "合同号")?.isNotBlank() == true
            val hasCustomerName = getValueByHeader(orderRow, table.headers, "客户名称")?.isNotBlank() == true
            val hasCustomerModel = getValueByHeader(orderRow, table.headers, "客户型号")?.isNotBlank() == true
            val hasSalesperson = getValueByHeader(orderRow, table.headers, "业务员")?.isNotBlank() == true
            val hasDeliveryTime = getValueByHeader(orderRow, table.headers, "交货时间")?.isNotBlank() == true
            
            // 如果有任何一个发货计划字段有值，认为是发货计划订单
            return hasCustomerContract || hasContractNumber || hasCustomerName || 
                   hasCustomerModel || hasSalesperson || hasDeliveryTime
        }
        
        return false
    }
    
    /**
     * 步骤3：根据发货计划表调整优先级
     */
    private fun adjustPriorityByShippingPlan(orders: List<ProductionOrder>, shippingPlanTable: TableData): List<ProductionOrder> {
        return orderFilter.adjustPriorityByShippingPlan(orders, shippingPlanTable)
    }
    
    /**
     * 步骤4：执行智能排产（使用指定策略）
     */
    private fun performSchedulingWithStrategy(orders: List<ProductionOrder>, machineRules: List<MachineRule>, strategy: SchedulingStrategy): SchedulingResult {
        // 创建机台列表
        val machines = createMachinesFromRules(machineRules)
        
        // 排产约束
        val constraints = SchedulingConstraints(
            workDaysPerMonth = 22,
            shiftHours = 8,
            bufferDays = 2,
            respectDeadline = true,
            considerCapacity = true,
            avoidOvertime = false,
            balanceLoad = true
        )
        // 机台可连续生产，不受单次生产限制
        
        // 执行排产
        return smartScheduler.schedule(orders, strategy, constraints, machines)
    }
    
    /**
     * 步骤4：执行智能排产
     */
    private fun performScheduling(orders: List<ProductionOrder>, machineRules: List<MachineRule>): SchedulingResult {
        // 创建机台列表
        val machines = createMachinesFromRules(machineRules)
        
        // 排产约束
        val constraints = SchedulingConstraints(
            workDaysPerMonth = 22,
            shiftHours = 8,
            bufferDays = 2,
            respectDeadline = true,
            considerCapacity = true,
            avoidOvertime = false,
            balanceLoad = true
        )
        // 机台可连续生产，不受单次生产限制
        
        // 执行排产
        return smartScheduler.schedule(orders, SchedulingStrategy.BALANCED, constraints, machines)
    }
    
    /**
     * 步骤5：生成排产计划表
     */
    private fun generateSchedulingPlanTable(mergedTable: TableData, schedulingResult: SchedulingResult): TableData {
        // 创建新的表头：订单、机台、排产状态、日期列
        val newHeaders = mutableListOf<String>()
        newHeaders.add("订单号")
        newHeaders.add("分配机台")
        newHeaders.add("排产状态")
        
        // 计算日期范围
        val allDates = schedulingResult.orders.flatMap { order ->
            listOfNotNull(order.startDate, order.endDate)
        }
        
        if (allDates.isNotEmpty()) {
            val startDate = allDates.minOrNull()!!
            val endDate = allDates.maxOrNull()!!
            
            // 添加日期列
            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                newHeaders.add(currentDate.format(DateTimeFormatter.ofPattern("MM月dd日")))
                currentDate = currentDate.plusDays(1)
            }
        } else {
            // 如果没有订单，使用当前日期作为默认范围
            val startDate = LocalDate.now()
            val endDate = startDate.plusDays(7)
            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                newHeaders.add(currentDate.format(DateTimeFormatter.ofPattern("MM月dd日")))
                currentDate = currentDate.plusDays(1)
            }
        }
        
        val planRows = mutableListOf<List<String>>()
        val planFormulas = mutableListOf<List<String?>>()
        
        // 确保显示所有1-7号机台，即使没有订单也要显示
        val allMachineIds = (1..7).map { "${it}#" }
        val machineGroups = allMachineIds.map { machineId ->
            val orders = schedulingResult.machineSchedule[machineId]?.sortedBy { it.startDate } ?: emptyList()
            machineId to orders
        }
        
        // 调试信息：打印排产结果
        println("🔍 排产结果调试信息:")
        println("  总订单数: ${schedulingResult.orders.size}")
        println("  机台排产计划: ${schedulingResult.machineSchedule.keys}")
        schedulingResult.machineSchedule.forEach { (machineId, orders) ->
            println("  机台 $machineId: ${orders.size} 个订单")
            orders.forEach { order ->
                println("    订单 ${order.id}: 开始=${order.startDate}, 结束=${order.endDate}, 数量=${order.quantity}, 换模=${order.moldChangeoverTime}h, 换管=${order.pipeChangeoverTime}h")
            }
        }
        
        // 使用SmartScheduler的排产结果直接生成排产计划表
        println("📋 使用SmartScheduler排产结果生成排产计划表...")
        
        // 为每个机台创建排产计划
        machineGroups.forEach { (machineId, orders) ->
            if (orders.isNotEmpty()) {
                // 为每个机台的每个订单创建一行
                orders.forEach { order ->
                    val planRow = mutableListOf<String>()
                    val planRowFormulas = mutableListOf<String?>()
                    
                    // 订单号
                    planRow.add(order.id)
                    planRowFormulas.add(null)
                    
                    // 机台
                    planRow.add(machineId)
                    planRowFormulas.add(null)
                    
                    // 排产状态
                    planRow.add("已排产")
                    planRowFormulas.add(null)
                    
                    // 计算日期范围
                    val startDate = order.startDate ?: return@forEach
                    val endDate = order.endDate ?: return@forEach
                    
                    // 为每个日期添加数据
                    val tableStartDate = allDates.minOrNull() ?: LocalDate.now()
                    val tableEndDate = allDates.maxOrNull() ?: LocalDate.now().plusDays(7)
                    
                    var currentDate = tableStartDate
                    while (!currentDate.isAfter(tableEndDate)) {
                        if (!currentDate.isBefore(startDate) && !currentDate.isAfter(endDate)) {
                            // 在订单日期范围内
                            if (currentDate.isEqual(startDate)) {
                                // 第一天：显示换模换管时间和生产时间
                                val totalChangeoverTime = order.moldChangeoverTime + order.pipeChangeoverTime
                                if (totalChangeoverTime > 0) {
                                    // 显示换模换管时间和生产数量
                                    planRow.add("${order.quantity} 换模(${order.moldChangeoverTime}h) 换管(${order.pipeChangeoverTime}h)")
                                } else {
                                    // 无需换模换管，直接生产
                                    planRow.add("${order.quantity}")
                                }
                            } else {
                                // 连续生产天：显示生产数量
                                planRow.add("${order.quantity}")
                            }
                        } else {
                            // 不在订单日期范围内
                            planRow.add("-")
                        }
                        planRowFormulas.add(null)
                        currentDate = currentDate.plusDays(1)
                    }
                    
                    planRows.add(planRow)
                    planFormulas.add(planRowFormulas)
                }
            } else {
                // 机台没有订单，显示空行
                val planRow = mutableListOf<String>()
                val planRowFormulas = mutableListOf<String?>()
                
                planRow.add("-") // 订单号为空
                planRowFormulas.add(null)
                
                planRow.add(machineId) // 机台号
                planRowFormulas.add(null)
                
                planRow.add("无排产") // 排产状态
                planRowFormulas.add(null)
                
                // 为所有日期添加"-"
                val tableStartDate = allDates.minOrNull() ?: LocalDate.now()
                val tableEndDate = allDates.maxOrNull() ?: LocalDate.now().plusDays(7)
                
                var currentDate = tableStartDate
                while (!currentDate.isAfter(tableEndDate)) {
                    planRow.add("-")
                    planRowFormulas.add(null)
                    currentDate = currentDate.plusDays(1)
                }
                
                planRows.add(planRow)
                planFormulas.add(planRowFormulas)
            }
        }
        
        return TableData(
            fileName = "排产计划表",
            headers = newHeaders,
            rows = planRows,
            formulas = planFormulas
        )
    }
    
    /**
     * 从机台规则创建机台列表
     */
    private fun createMachinesFromRules(machineRules: List<MachineRule>): List<Machine> {
        val machineMap = mutableMapOf<String, Machine>()
        
        machineRules.forEach { rule ->
            if (!machineMap.containsKey(rule.machineId)) {
                machineMap[rule.machineId] = Machine(
                    id = rule.machineId,
                    name = "机台${rule.machineId}",
                    capacity = 100, // 默认产能
                    efficiency = 1.0
                )
            }
        }
        
        return machineMap.values.toList()
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
        
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )
        
        for (formatter in formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter)
            } catch (e: Exception) {
                // 继续尝试下一个格式
            }
        }
        
        return null
    }
}

/**
 * 发货计划信息
 */
data class ShippingPlanInfo(
    val companyModel: String,
    val customerModel: String,
    val customerName: String,
    val deliveryDate: LocalDate,
    val quantity: Int
)

/**
 * 排产流程结果
 */
data class SchedulingFlowResult(
    val mergedTable: TableData,           // 合并后的表
    val filteredOrders: List<ProductionOrder>, // 筛选后的订单
    val excludedOrders: List<ProductionOrder> = emptyList(), // 不参与排产的订单（绿色标注）
    val schedulingResult: SchedulingResult,    // 排产结果
    val schedulingPlanTable: TableData        // 排产计划表
)
