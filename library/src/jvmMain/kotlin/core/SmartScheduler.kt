package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 智能排产调度器
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
        val pendingOrders = orders.filter { it.status == OrderStatus.PENDING }
        
        return when (strategy) {
            SchedulingStrategy.CAPACITY_FIRST -> capacityFirstScheduling(pendingOrders, availableMachines, constraints)
            SchedulingStrategy.TIME_FIRST -> timeFirstScheduling(pendingOrders, availableMachines, constraints)
            SchedulingStrategy.ORDER_FIRST -> orderFirstScheduling(pendingOrders, availableMachines, constraints)
            SchedulingStrategy.BALANCED -> balancedScheduling(pendingOrders, availableMachines, constraints)
        }
    }
    
    /**
     * 产能优先排产
     * 基于管子数×段数=总段数，每个机台一次生产1段
     */
    private fun capacityFirstScheduling(orders: List<ProductionOrder>, 
                                      machines: List<Machine>,
                                      constraints: SchedulingConstraints): SchedulingResult {
        // 按优先级排序：发货计划表优先 > 管子数量足够 > 紧急订单 > 其他
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { 
            when {
                it.priority == OrderPriority.URGENT -> 0  // 发货计划表订单
                it.priority == OrderPriority.HIGH -> 1
                it.priority == OrderPriority.MEDIUM -> 2
                else -> 3
            }
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先
        .thenBy { !it.isPipeArrived() } // 管子已到货的优先
        .thenBy { it.plannedDeliveryDate ?: LocalDate.MAX })
        
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        // 组合相同规格的订单进行生产
        val combinedOrders = combineOrdersBySpec(sortedOrders)
        
        // 按优先级和时间排序
        val finalOrders = combinedOrders.sortedWith(compareBy<ProductionOrder> { 
            when (it.priority) {
                OrderPriority.URGENT -> 0
                OrderPriority.HIGH -> 1
                OrderPriority.MEDIUM -> 2
                OrderPriority.LOW -> 3
            }
        }.thenBy { it.plannedDeliveryDate ?: LocalDate.MAX })
        
        // 并行排产：每个机台可以同时处理不同模具的订单
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        for (order in finalOrders) {
            // 计算总段数：管子数 × 段数
            val totalSegments = order.quantity * order.segments
            val productionDays = totalSegments.toDouble() / order.dailyProduction
            
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints)
            
            if (bestMachine != null) {
                val startDate = machineAvailability[bestMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id,
                    productionDays = productionDays,
                    remainingDays = productionDays
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 更新机台可用时间（考虑换模/换管时间）
                val changeoverTime = getChangeoverTime(order, bestMachine)
                machineAvailability[bestMachine.id] = endDate.plusDays((changeoverTime + 1).toLong())
            } else {
                conflicts.add("订单 ${order.id} 无法安排到合适的机台")
            }
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
     * 组合相同规格的订单
     * 将相同内外径、相同机台的订单组合在一起生产，减少换模/换管次数
     */
    private fun combineOrdersBySpec(orders: List<ProductionOrder>): List<ProductionOrder> {
        val combinedOrders = mutableListOf<ProductionOrder>()
        val processedOrders = mutableSetOf<String>()
        
        for (order in orders) {
            if (processedOrders.contains(order.id)) continue
            
            // 查找相同规格的订单（相同内外径）
            val sameSpecOrders = orders.filter { 
                it.id != order.id && 
                !processedOrders.contains(it.id) &&
                it.innerDiameter == order.innerDiameter && 
                it.outerDiameter == order.outerDiameter &&
                it.priority == order.priority  // 相同优先级的订单才能组合
            }
            
            if (sameSpecOrders.isNotEmpty()) {
                // 组合订单：合并数量，使用第一个订单的其他信息
                val totalQuantity = order.quantity + sameSpecOrders.sumOf { it.quantity }
                val combinedOrder = order.copy(
                    quantity = totalQuantity,
                    notes = "组合订单: ${order.id} + ${sameSpecOrders.joinToString(", ") { it.id }}"
                )
                
                combinedOrders.add(combinedOrder)
                processedOrders.add(order.id)
                processedOrders.addAll(sameSpecOrders.map { it.id })
            } else {
                // 没有相同规格的订单，单独处理
                combinedOrders.add(order)
                processedOrders.add(order.id)
            }
        }
        
        return combinedOrders
    }
    
    /**
     * 根据机台可用性找到最佳机台
     */
    private fun findBestMachineForOrderWithAvailability(
        order: ProductionOrder, 
        machines: List<Machine>, 
        machineAvailability: Map<String, LocalDate>,
        constraints: SchedulingConstraints
    ): Machine? {
        val suitableMachines = machines.filter { machine ->
            // 检查机台是否适合该订单（基于管规格）
            isMachineSuitableForOrder(order, machine)
        }
        
        if (suitableMachines.isEmpty()) return null
        
        // 选择可用时间最早的机台
        return suitableMachines.minByOrNull { machine ->
            machineAvailability[machine.id] ?: LocalDate.MAX
        }
    }
    
    /**
     * 检查机台是否适合订单
     */
    private fun isMachineSuitableForOrder(order: ProductionOrder, machine: Machine): Boolean {
        // 这里应该根据机台配置规则来判断
        // 暂时返回true，实际应该根据管规格匹配
        return true
    }
    
    /**
     * 获取换模/换管时间
     */
    private fun getChangeoverTime(order: ProductionOrder, machine: Machine): Int {
        // 根据机台配置规则获取换模/换管时间
        // 暂时返回默认值
        return 1 // 1天换模时间
    }
    
    /**
     * 时间优先排产
     */
    private fun timeFirstScheduling(orders: List<ProductionOrder>, 
                                   machines: List<Machine>,
                                   constraints: SchedulingConstraints): SchedulingResult {
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { 
            it.plannedDeliveryDate ?: LocalDate.MAX 
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先
        .thenBy { !it.isPipeArrived() }) // 管子已到货的优先
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        for (order in sortedOrders) {
            val bestMachine = findBestMachineForOrder(order, machines, machineSchedule, LocalDate.now(), constraints)
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = findEarliestStartDate(order, bestMachine, machineSchedule[bestMachine.id]!!, constraints)
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                // 检查是否能在交付期内完成
                if (constraints.respectDeadline && order.plannedDeliveryDate != null && endDate.isAfter(order.plannedDeliveryDate)) {
                    conflicts.add("订单 ${order.id} 无法在交付期内完成")
                    continue
                }
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
            } else {
                conflicts.add("订单 ${order.id} 无法安排到合适的机台")
            }
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
     * 订单优先排产
     * 只处理发货计划表中的订单（URGENT优先级）
     */
    private fun orderFirstScheduling(orders: List<ProductionOrder>, 
                                    machines: List<Machine>,
                                    constraints: SchedulingConstraints): SchedulingResult {
        // 订单优先策略：只处理发货计划表中的订单（URGENT优先级）
        val shippingPlanOrders = orders.filter { it.priority == OrderPriority.URGENT }
        
        if (shippingPlanOrders.isEmpty()) {
            // 如果没有发货计划订单，返回空结果
            return SchedulingResult(
                orders = emptyList(),
                machineSchedule = emptyMap(),
                totalProductionDays = 0,
                conflicts = listOf("没有找到发货计划表中的订单"),
                utilizationRate = 0.0,
                onTimeDeliveryRate = 0.0
            )
        }
        
        // 对发货计划订单按交付期、管子数量、数量排序
        val sortedOrders = shippingPlanOrders.sortedWith(compareBy<ProductionOrder> { 
            // 按交付期排序
            it.plannedDeliveryDate ?: LocalDate.MAX 
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先
        .thenBy { !it.isPipeArrived() } // 管子已到货的优先
        .thenBy { 
            // 按未发数量排序（数量多的优先）
            -it.unshippedQuantity 
        })
        
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        for (order in sortedOrders) {
            val bestMachine = findBestMachineForOrder(order, machines, machineSchedule, LocalDate.now(), constraints)
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = findEarliestStartDate(order, bestMachine, machineSchedule[bestMachine.id]!!, constraints)
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
            } else {
                conflicts.add("订单 ${order.id} 无法安排到合适的机台")
            }
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
     * 平衡策略排产
     */
    private fun balancedScheduling(orders: List<ProductionOrder>, 
                                  machines: List<Machine>,
                                  constraints: SchedulingConstraints): SchedulingResult {
        // 综合考虑产能、时间、订单优先级、管子数量
        val weightedOrders = orders.map { order ->
            val priorityWeight = when (order.priority) {
                OrderPriority.URGENT -> 4.0
                OrderPriority.HIGH -> 3.0
                OrderPriority.MEDIUM -> 2.0
                OrderPriority.LOW -> 1.0
            }
            
            val urgencyWeight = if (order.isUrgent()) 2.0 else 1.0
            val quantityWeight = order.quantity.toDouble() / (orders.maxOfOrNull { it.quantity }?.toDouble() ?: 1.0)
            
            // 管子数量权重：管子数量足够的订单权重更高
            val pipeWeight = if (order.hasEnoughPipeQuantity()) 2.0 else 0.5
            val pipeArrivalWeight = if (order.isPipeArrived()) 1.5 else 0.8
            
            val totalWeight = priorityWeight * urgencyWeight * quantityWeight * pipeWeight * pipeArrivalWeight
            
            order to totalWeight
        }.sortedByDescending { it.second }.map { it.first }
        
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        for (order in weightedOrders) {
            val bestMachine = findBestMachineForOrder(order, machines, machineSchedule, LocalDate.now(), constraints)
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = findEarliestStartDate(order, bestMachine, machineSchedule[bestMachine.id]!!, constraints)
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
            } else {
                conflicts.add("订单 ${order.id} 无法安排到合适的机台")
            }
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
     * 为订单找到最佳机台
     */
    private fun findBestMachineForOrder(order: ProductionOrder, 
                                      machines: List<Machine>,
                                      machineSchedule: Map<String, MutableList<ProductionOrder>>,
                                      startDate: LocalDate,
                                      constraints: SchedulingConstraints): Machine? {
        // 使用机台分配引擎
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        if (assignment != null) {
            // 找到对应的机台
            return machines.find { it.id == assignment.machineId }
        }
        
        // 如果机台分配引擎没有找到合适的机台，使用原来的逻辑
        return machines.minByOrNull { machine ->
            val scheduledOrders = machineSchedule[machine.id] ?: emptyList()
            val lastOrderEndDate = scheduledOrders.maxOfOrNull { it.endDate ?: LocalDate.MIN } ?: startDate
            val availableDate = maxOf(lastOrderEndDate.plusDays(1), startDate)
            
            // 计算等待时间
            val waitingDays = ChronoUnit.DAYS.between(startDate, availableDate).toInt()
            
            // 计算产能匹配度
            val capacityMatch = if (machine.capacity > 0) {
                kotlin.math.abs(order.dailyProduction - machine.capacity).toDouble() / machine.capacity
            } else {
                1.0
            }
            
            // 综合评分（等待时间 + 产能匹配度）
            waitingDays + capacityMatch * 10
        }
    }
    
    /**
     * 找到订单的最早开始日期
     */
    private fun findEarliestStartDate(order: ProductionOrder, 
                                    machine: Machine,
                                    scheduledOrders: List<ProductionOrder>,
                                    constraints: SchedulingConstraints): LocalDate {
        val sortedScheduledOrders = scheduledOrders.sortedBy { it.startDate }
        var earliestDate = LocalDate.now()
        
        for (scheduledOrder in sortedScheduledOrders) {
            if (scheduledOrder.endDate != null && scheduledOrder.endDate!!.isAfter(earliestDate)) {
                earliestDate = scheduledOrder.endDate!!.plusDays(1)
            }
        }
        
        return earliestDate
    }
    
    /**
     * 计算总生产天数
     */
    private fun calculateTotalProductionDays(orders: List<ProductionOrder>): Int {
        return orders.sumOf { it.calculateProductionDays() }
    }
    
    /**
     * 计算机台利用率
     */
    private fun calculateUtilizationRate(machineSchedule: Map<String, List<ProductionOrder>>, 
                                       machines: List<Machine>): Double {
        if (machines.isEmpty()) return 0.0
        
        val totalCapacity = machines.sumOf { it.capacity }
        val totalUsed = machineSchedule.values.sumOf { orders ->
            orders.sumOf { it.calculateProductionDays() * it.dailyProduction }
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
