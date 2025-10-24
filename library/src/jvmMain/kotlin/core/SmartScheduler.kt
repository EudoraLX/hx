package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 智能排产调度器
 * 简化版本：只保留机台规则匹配和产量计算
 */
class SmartScheduler {
    
    /**
     * 执行智能排产
     */
    fun schedule(orders: List<ProductionOrder>, 
                strategy: SchedulingStrategy,
                constraints: SchedulingConstraints,
                machines: List<Machine>): SchedulingResult {
        
        val availableMachines = machines.filter { it.isAvailable }
        
        println("🔍 排产引擎订单状态分析:")
        println("  - 输入订单数: ${orders.size}")
        
        // 统计各种状态的订单
        val statusCounts = orders.groupBy { it.status }.mapValues { it.value.size }
        println("  - 状态分布: $statusCounts")
        
        // 移除状态过滤，排产所有筛选后的订单
        println("  - 参与排产订单数: ${orders.size} (所有筛选后的订单)")
        
        // 所有策略都使用相同的简化逻辑
        return simplifiedScheduling(orders, availableMachines, constraints)
    }
    
    /**
     * 简化排产逻辑
     * 只保留机台规则匹配和产量计算
     * 1. 排产所有筛选后的订单
     * 2. 排产数量 = 未发货数量 - 注塑完成
     * 3. 根据机台规则匹配机台
     * 4. 换模换管时间不变，两个操作可拆开
     */
    private fun simplifiedScheduling(orders: List<ProductionOrder>, 
                                   machines: List<Machine>,
                                   constraints: SchedulingConstraints): SchedulingResult {
        println("=== 开始排产 ===")
        println("📊 排产输入统计:")
        println("  - 总订单数: ${orders.size}")
        println("  - 可用机台数: ${machines.size}")
        println("  - 机台列表: ${machines.map { it.id }}")
        
        // 统计订单状态
        val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
        val completedOrders = orders.filter { it.status == OrderStatus.COMPLETED }
        val inProductionOrders = orders.filter { it.status == OrderStatus.IN_PRODUCTION }
        
        println("📊 订单状态统计:")
        println("  - 待排产: ${pendingOrders.size}")
        println("  - 已完成: ${completedOrders.size}")
        println("  - 生产中: ${inProductionOrders.size}")
        
        // 统计排产数量
        var zeroQuantityCount = 0
        var positiveQuantityCount = 0
        orders.forEach { order ->
            val quantity = adjustQuantityByPipeStatus(order)
            if (quantity <= 0) {
                zeroQuantityCount++
            } else {
                positiveQuantityCount++
            }
        }
        
        println("📊 排产数量统计:")
        println("  - 排产数量>0: ${positiveQuantityCount}")
        println("  - 排产数量=0: ${zeroQuantityCount}")
        
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        // 机台可用时间
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        // 排产所有筛选后的订单
        var processedCount = 0
        var skippedCount = 0
        var scheduledCount = 0
        
        for (order in orders) {
            processedCount++
            println("\n--- 处理订单 ${order.id} (${processedCount}/${orders.size}) ---")
            println("订单信息: 内径=${order.innerDiameter}, 外径=${order.outerDiameter}, 未发货=${order.unshippedQuantity}, 注塑完成=${order.injectionCompleted}")
            
            // 排产数量 = 未发货数 - 注塑完成
            val productionQuantity = adjustQuantityByPipeStatus(order)
            println("排产数量: $productionQuantity")
            
            if (productionQuantity <= 0) {
                println("⚠️ 订单 ${order.id} 排产数量为0，跳过排产")
                skippedCount++
                continue
            }
            
            // 计算生产天数
            val productionDays = if (order.dailyProduction > 0) {
                kotlin.math.ceil(productionQuantity.toDouble() / order.dailyProduction)
            } else {
                kotlin.math.ceil(order.productionDays)
            }
            println("生产天数: $productionDays")
            
            // 根据机台规则匹配机台
            println("开始机台匹配...")
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints)
            if (bestMachine != null) {
                println("✅ 找到最佳机台: ${bestMachine.id}")
            } else {
                println("❌ 未找到最佳机台，尝试备用机台...")
                val fallbackMachine = findFallbackMachine(order, machines, machineAvailability)
                if (fallbackMachine != null) {
                    println("✅ 找到备用机台: ${fallbackMachine.id}")
                } else {
                    println("❌ 未找到任何可用机台")
                }
            }
            
            val finalMachine = bestMachine ?: findFallbackMachine(order, machines, machineAvailability)
            
            if (finalMachine != null) {
                val startDate = machineAvailability[finalMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                println("✅ 订单 ${order.id} 排产成功")
                println("机台: ${finalMachine.id}, 开始: $startDate, 结束: $endDate")
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    schedulingStatus = SchedulingStatus.SCHEDULED,
                    machine = finalMachine.id,
                    productionDays = productionDays,
                    remainingDays = productionDays,
                    quantity = productionQuantity
                )
                
                machineSchedule[finalMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                scheduledCount++
                
                // 换模换管时间不变，两个操作可拆开
                val moldChangeoverTime = getMoldChangeoverTime(order, finalMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, finalMachine)
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { if (it > 0) it.toLong() else 1L }
                machineAvailability[finalMachine.id] = endDate.plusDays(changeoverDays)
                
                println("机台 ${finalMachine.id} 下次可用时间: ${machineAvailability[finalMachine.id]}")
            } else {
                val conflictMsg = "订单 ${order.id} 无法安排到合适的机台"
                println("❌ $conflictMsg")
                conflicts.add(conflictMsg)
            }
        }
        
        println("\n=== 排产完成 ===")
        println("📊 详细统计:")
        println("  - 总处理订单数: $processedCount")
        println("  - 跳过订单数: $skippedCount (排产数量=0)")
        println("  - 成功排产数: $scheduledCount")
        println("  - 排产失败数: ${conflicts.size}")
        println("  - 实际排产订单数: ${scheduledOrders.size}")
        
        println("\n📊 数量验证:")
        println("  - 输入订单数: ${orders.size}")
        println("  - 处理订单数: $processedCount")
        println("  - 跳过+成功+失败: ${skippedCount + scheduledCount + conflicts.size}")
        println("  - 数量是否一致: ${processedCount == skippedCount + scheduledCount + conflicts.size}")
        
        if (conflicts.isNotEmpty()) {
            println("\n=== 排产冲突详情 ===")
            conflicts.forEach { conflict ->
                println("❌ $conflict")
            }
        }
        
        if (skippedCount > 0) {
            println("\n=== 跳过订单详情 ===")
            println("⚠️ 有 $skippedCount 个订单因排产数量为0被跳过")
        }
        
        return SchedulingResult(
            orders = scheduledOrders,
            machineSchedule = machineSchedule,
            totalProductionDays = calculateTotalProductionDays(scheduledOrders),
            utilizationRate = calculateUtilizationRate(machineSchedule, machines),
            onTimeDeliveryRate = calculateOnTimeDeliveryRate(scheduledOrders),
            conflicts = conflicts
        )
    }
    
    /**
     * 根据机台可用性找到最佳机台
     * 以机台配置表为核心，优先选择产能利用率最高的机台
     */
    private fun findBestMachineForOrderWithAvailability(
        order: ProductionOrder, 
        machines: List<Machine>, 
        machineAvailability: Map<String, LocalDate>,
        constraints: SchedulingConstraints
    ): Machine? {
        println("  🔍 开始机台分配引擎匹配...")
        
        // 使用机台分配引擎获取最适合的机台
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        if (assignment != null) {
            println("  ✅ 机台分配引擎找到匹配: 机台${assignment.machineId}, 模具${assignment.moldId}")
            // 找到对应的机台
            val assignedMachine = machines.find { it.id == assignment.machineId }
            if (assignedMachine != null && assignedMachine.isAvailable) {
                println("  ✅ 机台${assignment.machineId}可用，分配成功")
                return assignedMachine
            } else {
                println("  ❌ 机台${assignment.machineId}不可用或不存在")
            }
        } else {
            println("  ❌ 机台分配引擎未找到匹配")
        }
        
        println("  🔍 尝试备用机台匹配...")
        // 如果没有找到合适的机台，使用原来的逻辑
        val suitableMachines = machines.filter { machine ->
            machine.isAvailable && isMachineSuitableForOrder(order, machine)
        }
        
        println("  📊 适合的机台数: ${suitableMachines.size}")
        
        // 如果没有合适的机台，选择任何可用的机台（确保所有订单都能排产）
        val availableMachines = if (suitableMachines.isEmpty()) {
            println("  ⚠️ 没有适合的机台，使用所有可用机台")
            machines.filter { it.isAvailable }
        } else {
            suitableMachines
        }
        
        if (availableMachines.isEmpty()) {
            println("  ❌ 没有可用机台")
            return null
        }
        
        println("  📊 可用机台数: ${availableMachines.size}")
        
        // 选择可用时间最早且产能利用率最高的机台
        val selectedMachine = availableMachines.minByOrNull { machine ->
            val availabilityDate = machineAvailability[machine.id] ?: LocalDate.MAX
            val capacityUtilization = machine.capacity.toDouble() / 100.0 // 假设100为满产能
            // 综合考虑可用时间和产能利用率
            ChronoUnit.DAYS.between(LocalDate.now(), availabilityDate).toInt() + (1.0 / capacityUtilization).toInt()
        }
        
        if (selectedMachine != null) {
            println("  ✅ 选择机台: ${selectedMachine.id}")
        } else {
            println("  ❌ 无法选择机台")
        }
        
        return selectedMachine
    }
    
    /**
     * 检查机台是否适合订单
     * 基于机台配置表，检查机台是否能处理该订单的管规格
     */
    private fun isMachineSuitableForOrder(order: ProductionOrder, machine: Machine): Boolean {
        // 使用机台分配引擎检查机台是否适合
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        // 如果机台分配引擎能找到合适的机台，且机台ID匹配，则适合
        return assignment?.machineId == machine.id
    }
    
    /**
     * 备用机台分配方法
     * 根据机台配置表的说明列进行外径范围匹配
     * 当无法找到精确匹配的机台时，使用备用策略确保所有订单都能排产
     */
    private fun findFallbackMachine(order: ProductionOrder, machines: List<Machine>, machineAvailability: Map<String, LocalDate>): Machine? {
        val orderOuterDiameter = order.outerDiameter
        println("  🔍 备用机台分配 - 外径: $orderOuterDiameter")
        
        // 根据机台配置表的说明列进行外径范围匹配
        val suitableMachine = when {
            // 2#机台：Ø150外径及以下
            orderOuterDiameter <= 150 -> {
                println("  📋 匹配规则: ≤150mm -> 2#机台")
                machines.find { it.id == "2#" }
            }
            
            // 3#机台：Ø160~Ø218外径
            orderOuterDiameter in 160.0..218.0 -> {
                println("  📋 匹配规则: 160-218mm -> 3#机台")
                machines.find { it.id == "3#" }
            }
            
            // 4#机台：Ø250~Ø272、Ø414外径 机动5#
            orderOuterDiameter in 250.0..272.0 || orderOuterDiameter == 414.0 -> {
                println("  📋 匹配规则: 250-272mm或414mm -> 4#机台")
                machines.find { it.id == "4#" }
            }
            
            // 5#机台：Ø290~Ø400外径 机动4#
            orderOuterDiameter in 290.0..400.0 -> {
                println("  📋 匹配规则: 290-400mm -> 5#机台")
                machines.find { it.id == "5#" }
            }
            
            // 6#机台：Ø280、Ø510外径和两个大锥
            orderOuterDiameter == 280.0 || orderOuterDiameter == 510.0 -> {
                println("  📋 匹配规则: 280mm或510mm -> 6#机台")
                machines.find { it.id == "6#" }
            }
            
            // 7#机台：Ø600外径
            orderOuterDiameter == 600.0 -> {
                println("  📋 匹配规则: 600mm -> 7#机台")
                machines.find { it.id == "7#" }
            }
            
            // 对于不在明确范围内的外径，使用就近原则
            orderOuterDiameter in 151.0..159.0 -> {
                println("  📋 就近原则: 151-159mm -> 3#机台")
                machines.find { it.id == "3#" }
            }
            orderOuterDiameter in 219.0..249.0 -> {
                println("  📋 就近原则: 219-249mm -> 4#机台")
                machines.find { it.id == "4#" }
            }
            orderOuterDiameter in 273.0..289.0 -> {
                println("  📋 就近原则: 273-289mm -> 5#机台")
                machines.find { it.id == "5#" }
            }
            orderOuterDiameter in 401.0..413.0 || orderOuterDiameter in 415.0..509.0 -> {
                println("  📋 就近原则: 401-413mm或415-509mm -> 6#机台")
                machines.find { it.id == "6#" }
            }
            orderOuterDiameter > 600.0 -> {
                println("  📋 就近原则: >600mm -> 7#机台")
                machines.find { it.id == "7#" }
            }
            
            else -> {
                println("  ❌ 外径 $orderOuterDiameter 不在任何匹配范围内")
                null
            }
        }
        
        if (suitableMachine != null && suitableMachine.isAvailable) {
            println("  ✅ 备用机台分配成功: ${suitableMachine.id}")
            return suitableMachine
        } else if (suitableMachine != null) {
            println("  ❌ 匹配的机台${suitableMachine.id}不可用")
        }
        
        // 策略2：如果策略1失败，选择任何可用的机台
        println("  🔍 策略2: 选择任何可用机台")
        val availableMachines = machines.filter { it.isAvailable }
        println("  📊 可用机台: ${availableMachines.map { it.id }}")
        
        if (availableMachines.isNotEmpty()) {
            // 选择可用时间最早的机台
            val selectedMachine = availableMachines.minByOrNull { machine ->
                machineAvailability[machine.id] ?: LocalDate.now()
            }
            if (selectedMachine != null) {
                println("  ✅ 选择最早可用机台: ${selectedMachine.id}")
            }
            return selectedMachine
        }
        
        println("  ❌ 没有可用机台")
        return null
    }
    
    /**
     * 根据管子情况调整排产数量
     * 排产数量 = 未发货数量 - 注塑完成
     * 由于已筛选出不需要生产的订单，参与排产的订单生产数量必定大于0
     */
    private fun adjustQuantityByPipeStatus(order: ProductionOrder): Int {
        val unshippedQuantity = order.unshippedQuantity
        val injectionCompleted = order.injectionCompleted ?: 0
        
        // 排产数量 = 未发货数量 - 注塑完成
        val productionQuantity = maxOf(0, unshippedQuantity - injectionCompleted)
        
        return productionQuantity
    }
    
    /**
     * 获取换模时间（单独计算）
     */
    private fun getMoldChangeoverTime(order: ProductionOrder, machine: Machine): Int {
        return 12 // 换模时间12小时
    }
    
    /**
     * 获取换管时间（单独计算）
     */
    private fun getPipeChangeoverTime(order: ProductionOrder, machine: Machine): Int {
        return 4 // 换管时间4小时
    }
    
    /**
     * 计算总生产天数
     */
    private fun calculateTotalProductionDays(orders: List<ProductionOrder>): Int {
        return orders.sumOf { it.calculateProductionDays() }
    }
    
    /**
     * 计算机台利用率
     * 以机台配置表为核心，最大化产能利用率
     */
    private fun calculateUtilizationRate(machineSchedule: Map<String, List<ProductionOrder>>, 
                                       machines: List<Machine>): Double {
        if (machines.isEmpty()) return 0.0
        
        val totalCapacity = machines.sumOf { it.capacity }
        val totalUsed = machineSchedule.values.sumOf { orders ->
            orders.sumOf { order ->
                // 计算实际生产量：排产数量 × 段数 × 日产量
                val productionQuantity = adjustQuantityByPipeStatus(order)
                val totalSegments = productionQuantity * order.segments
                val productionDays = if (order.dailyProduction > 0) {
                    totalSegments.toDouble() / order.dailyProduction
                } else {
                    order.productionDays
                }
                productionDays * order.dailyProduction
            }
        }
        
        return if (totalCapacity > 0) totalUsed.toDouble() / totalCapacity else 0.0
    }
    
    /**
     * 计算按时交付率
     */
    private fun calculateOnTimeDeliveryRate(orders: List<ProductionOrder>): Double {
        if (orders.isEmpty()) return 0.0
        
        val onTimeOrders = orders.count { order ->
            order.plannedDeliveryDate == null || 
            (order.endDate != null && !order.endDate!!.isAfter(order.plannedDeliveryDate))
        }
        
        return onTimeOrders.toDouble() / orders.size
    }
    
    /**
     * 生成排产统计信息
     */
    fun generateStatistics(orders: List<ProductionOrder>): SchedulingStatistics {
        val totalOrders = orders.size
        val completedOrders = orders.count { it.status == OrderStatus.COMPLETED }
        val pendingOrders = orders.count { it.status == OrderStatus.PENDING }
        val urgentOrders = orders.count { it.priority == OrderPriority.URGENT }
        val totalQuantity = orders.sumOf { it.quantity }
        val averageProductionDays = if (orders.isNotEmpty()) {
            orders.map { it.calculateProductionDays() }.average()
        } else 0.0
        
        val machineUtilization = orders.groupBy { it.machine }
            .mapValues { (_, orders) ->
                orders.sumOf { it.calculateProductionDays() }.toDouble() / 30.0 // 假设30天为基准
            }
        
        return SchedulingStatistics(
            totalOrders = totalOrders,
            completedOrders = completedOrders,
            pendingOrders = pendingOrders,
            urgentOrders = urgentOrders,
            totalQuantity = totalQuantity,
            averageProductionDays = averageProductionDays,
            machineUtilization = machineUtilization
        )
    }
}