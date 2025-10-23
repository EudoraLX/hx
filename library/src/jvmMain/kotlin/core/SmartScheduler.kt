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
     * 以机台配置表为核心，管子数仅决定排产优先级，最大化生产
     * 新约束条件：
     * 1. 所有参与排产的订单都进行排产
     * 2. 排产数量 = 未发货数量 - 注射完成
     * 3. 管子数仅决定排产优先级
     * 4. 以机台配置分配为核心
     * 5. 最大化产能利用率
     */
    private fun capacityFirstScheduling(orders: List<ProductionOrder>, 
                                      machines: List<Machine>,
                                      constraints: SchedulingConstraints): SchedulingResult {
        // 按优先级排序：发货计划表优先 > 管子数量足够 > 紧急订单 > 其他
        // 管子数充足的优先排产，管子数不足的后排产（但仍要排产）
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { 
            when {
                it.priority == OrderPriority.URGENT -> 0  // 发货计划表订单
                it.priority == OrderPriority.HIGH -> 1
                it.priority == OrderPriority.MEDIUM -> 2
                else -> 3
            }
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先（管子数决定优先级）
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
        
        // 连续排产：每个机台可连续生产，不受单次生产限制
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        for (order in finalOrders) {
            // 根据管子情况调整排产数量
            val adjustedQuantity = adjustQuantityByPipeStatus(order)
            
            // 确保所有参与排产的订单都进行排产
            val productionQuantity = if (adjustedQuantity <= 0) {
                // 如果调整后数量为0，但仍有未发货数，则排产1个
                if (order.unshippedQuantity > 0) {
                    1
                } else {
                    continue // 跳过真正不需要排产的订单
                }
            } else {
                adjustedQuantity
            }
            
            // 计算总段数：(数量*段数)/日产量=生产天数
            val totalSegments = productionQuantity * order.segments
            val productionDays = if (order.dailyProduction > 0) {
                totalSegments.toDouble() / order.dailyProduction
            } else {
                order.productionDays
            }
            
            // 确保所有订单都能找到合适的机台进行排产
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints) 
                ?: machines.minByOrNull { machine -> 
                    // 使用机台可用时间映射，如果没有则使用当前时间
                    machineAvailability[machine.id] ?: LocalDate.now()
                } // 如果找不到最佳机台，选择最早可用的机台
            
            if (bestMachine != null) {
                val startDate = machineAvailability[bestMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id,
                    productionDays = productionDays,
                    remainingDays = productionDays,
                    quantity = adjustedQuantity
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 更新机台可用时间（考虑换模/换管时间）
                // 换模和换管可以不同时间进行，优化生产安排
                val moldChangeoverTime = getMoldChangeoverTime(order, bestMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, bestMachine)
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { if (it > 0) it.toLong() else 1L }
                machineAvailability[bestMachine.id] = endDate.plusDays(changeoverDays)
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
     * 以机台配置表为核心，将相同模具和管子规格的订单组合生产，减少换模/换管次数
     * 机台可连续生产，支持长时间连续作业，最大化产能利用率
     */
    private fun combineOrdersBySpec(orders: List<ProductionOrder>): List<ProductionOrder> {
        val combinedOrders = mutableListOf<ProductionOrder>()
        val processedOrders = mutableSetOf<String>()
        
        // 使用机台分配引擎获取机台配置信息
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        
        for (order in orders) {
            if (processedOrders.contains(order.id)) continue
            
            // 获取订单的机台分配信息
            val orderAssignment = machineAssignmentEngine.assignMachine(order, machineRules)
            val orderMachineId = orderAssignment?.machineId
            val orderMoldId = orderAssignment?.moldId
            
            // 查找相同机台、相同模具的订单（减少换模次数）
            val sameMachineMoldOrders = orders.filter { otherOrder ->
                otherOrder.id != order.id && 
                !processedOrders.contains(otherOrder.id) &&
                otherOrder.innerDiameter == order.innerDiameter && 
                otherOrder.outerDiameter == order.outerDiameter &&
                otherOrder.priority == order.priority  // 相同优先级的订单才能组合
            }.filter { otherOrder ->
                // 检查是否使用相同机台和模具
                val otherAssignment = machineAssignmentEngine.assignMachine(otherOrder, machineRules)
                otherAssignment?.machineId == orderMachineId && otherAssignment?.moldId == orderMoldId
            }
            
            if (sameMachineMoldOrders.isNotEmpty()) {
                // 组合订单：合并数量，使用第一个订单的其他信息
                val totalQuantity = order.quantity + sameMachineMoldOrders.sumOf { it.quantity }
                val combinedOrder = order.copy(
                    quantity = totalQuantity,
                    notes = "组合订单(相同模具): ${order.id} + ${sameMachineMoldOrders.joinToString(", ") { it.id }}"
                )
                
                combinedOrders.add(combinedOrder)
                processedOrders.add(order.id)
                processedOrders.addAll(sameMachineMoldOrders.map { it.id })
            } else {
                // 没有相同机台模具的订单，单独处理
                combinedOrders.add(order)
                processedOrders.add(order.id)
            }
        }
        
        return combinedOrders
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
        // 使用机台分配引擎获取最适合的机台
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        if (assignment != null) {
            // 找到对应的机台
            val assignedMachine = machines.find { it.id == assignment.machineId }
            if (assignedMachine != null && assignedMachine.isAvailable) {
                return assignedMachine
            }
        }
        
        // 如果没有找到合适的机台，使用原来的逻辑
        val suitableMachines = machines.filter { machine ->
            machine.isAvailable && isMachineSuitableForOrder(order, machine)
        }
        
        if (suitableMachines.isEmpty()) return null
        
        // 选择可用时间最早且产能利用率最高的机台
        return suitableMachines.minByOrNull { machine ->
            val availabilityDate = machineAvailability[machine.id] ?: LocalDate.MAX
            val capacityUtilization = machine.capacity.toDouble() / 100.0 // 假设100为满产能
            // 综合考虑可用时间和产能利用率
            ChronoUnit.DAYS.between(LocalDate.now(), availabilityDate).toInt() + (1.0 / capacityUtilization).toInt()
        }
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
     * 根据管子情况调整排产数量
     * 管子数仅决定排产优先级，不影响排产数量
     * 排产数量 = 未发货数量 - 注射完成
     * 确保所有参与排产的订单都进行排产
     */
    private fun adjustQuantityByPipeStatus(order: ProductionOrder): Int {
        val unshippedQuantity = order.unshippedQuantity
        val injectionCompleted = order.injectionCompleted ?: 0
        
        // 排产数量 = 未发货数量 - 注射完成
        val productionQuantity = maxOf(0, unshippedQuantity - injectionCompleted)
        
        // 确保所有参与排产的订单都进行排产
        return if (productionQuantity > 0) {
            productionQuantity
        } else if (unshippedQuantity > 0) {
            // 如果注射完成 >= 未发货数，但仍有未发货数，则排产1个
            1
        } else {
            0
        }
    }
    
    /**
     * 获取换模/换管时间
     * 换模和换管可以不同时间进行，优化生产安排
     * 机台可连续生产，换模/换管时间按实际小时计算
     */
    private fun getChangeoverTime(order: ProductionOrder, machine: Machine): Int {
        // 根据机台配置规则获取换模/换管时间
        // 换模时间12小时，换管时间4小时
        // 换模和换管可以不同时间进行，可以并行或错开安排
        // 例如：晚上23点开始换管则次日3点完成，可以跨天完成
        return 16 // 16小时换模/换管时间（12小时换模 + 4小时换管）
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
        
        // 连续排产：每个机台可连续生产，不受单次生产限制
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        for (order in sortedOrders) {
            // 确保所有参与排产的订单都进行排产
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints)
                ?: machines.minByOrNull { machine -> 
                    // 使用机台可用时间映射，如果没有则使用当前时间
                    machineAvailability[machine.id] ?: LocalDate.now()
                } // 如果找不到最佳机台，选择最早可用的机台
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = machineAvailability[bestMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                // 检查是否能在交付期内完成（但即使超期也要排产）
                if (constraints.respectDeadline && order.plannedDeliveryDate != null && endDate.isAfter(order.plannedDeliveryDate)) {
                    conflicts.add("订单 ${order.id} 无法在交付期内完成，但仍会排产")
                }
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 机台可连续生产，更新机台可用时间时考虑换模/换管时间
                // 换模和换管可以不同时间进行，优化生产安排
                val moldChangeoverTime = getMoldChangeoverTime(order, bestMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, bestMachine)
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { if (it > 0) it.toLong() else 1L }
                // 连续生产模式下，机台在完成一个订单后经过换模/换管时间即可开始下一个订单
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
     * 根据公司型号与输出表联系标记出订单优先的订单，作为优先排产，其他的继续进行交付期优先排产
     */
    private fun orderFirstScheduling(orders: List<ProductionOrder>, 
                                    machines: List<Machine>,
                                    constraints: SchedulingConstraints): SchedulingResult {
        // 订单优先策略：优先处理发货计划表中的订单（URGENT优先级），其他按交付期优先
        val priorityOrders = orders.filter { it.priority == OrderPriority.URGENT }
        val otherOrders = orders.filter { it.priority != OrderPriority.URGENT }
        
        // 对优先订单按交付期、管子数量、数量排序
        // 管子数充足的优先排产，管子数不足的后排产（但仍要排产）
        val sortedPriorityOrders = priorityOrders.sortedWith(compareBy<ProductionOrder> { 
            // 按交付期排序
            it.plannedDeliveryDate ?: LocalDate.MAX 
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先
        .thenBy { !it.isPipeArrived() } // 管子已到货的优先
        .thenBy { 
            // 按未发数量排序（数量多的优先）
            -it.unshippedQuantity 
        })
        
        // 对其他订单按交付期排序
        // 管子数充足的优先排产，管子数不足的后排产（但仍要排产）
        val sortedOtherOrders = otherOrders.sortedWith(compareBy<ProductionOrder> { 
            it.plannedDeliveryDate ?: LocalDate.MAX 
        }.thenBy { !it.hasEnoughPipeQuantity() } // 管子数量足够的优先
        .thenBy { !it.isPipeArrived() } // 管子已到货的优先
        .thenBy { -it.unshippedQuantity })
        
        // 合并排序后的订单列表
        val sortedOrders = sortedPriorityOrders + sortedOtherOrders
        
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        // 连续排产：每个机台可连续生产，不受单次生产限制
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        for (order in sortedOrders) {
            // 根据管子情况调整排产数量
            val adjustedQuantity = adjustQuantityByPipeStatus(order)
            
            // 计算生产天数：(数量*段数)/日产量=生产天数
            val totalSegments = adjustedQuantity * order.segments
            val productionDays = if (order.dailyProduction > 0) {
                totalSegments.toDouble() / order.dailyProduction
            } else {
                order.productionDays
            }
            
            // 确保所有订单都能找到合适的机台进行排产
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints)
                ?: machines.minByOrNull { machine -> 
                    // 使用机台可用时间映射，如果没有则使用当前时间
                    machineAvailability[machine.id] ?: LocalDate.now()
                } // 如果找不到最佳机台，选择最早可用的机台
            
            if (bestMachine != null) {
                val startDate = machineAvailability[bestMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id,
                    quantity = adjustedQuantity,
                    productionDays = productionDays
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 机台可连续生产，完成订单后可以立即开始下一个订单（考虑换模时间）
                // 换模和换管可以不同时间进行，优化生产安排
                val moldChangeoverTime = getMoldChangeoverTime(order, bestMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, bestMachine)
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { if (it > 0) it.toLong() else 1L }
                // 连续生产模式：机台在完成一个订单后，经过换模/换管时间即可开始下一个订单
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
            
            // 管子数量权重：管子数量足够的订单权重更高，但管子数不足的仍要排产
            val pipeWeight = if (order.hasEnoughPipeQuantity()) 2.0 else 1.0 // 管子数不足的权重降低但不为0
            val pipeArrivalWeight = if (order.isPipeArrived()) 1.5 else 1.0 // 管子未到货的权重降低但不为0
            
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
        
        // 连续排产：每个机台可连续生产，不受单次生产限制
        val machineAvailability = mutableMapOf<String, LocalDate>()
        machines.forEach { machine ->
            machineAvailability[machine.id] = LocalDate.now()
        }
        
        for (order in weightedOrders) {
            // 确保所有订单都能找到合适的机台进行排产
            val bestMachine = findBestMachineForOrderWithAvailability(order, machines, machineAvailability, constraints)
                ?: machines.minByOrNull { machine -> 
                    // 使用机台可用时间映射，如果没有则使用当前时间
                    machineAvailability[machine.id] ?: LocalDate.now()
                } // 如果找不到最佳机台，选择最早可用的机台
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = machineAvailability[bestMachine.id]!!
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 机台可连续生产，完成订单后可以立即开始下一个订单（考虑换模时间）
                // 换模和换管可以不同时间进行，优化生产安排
                val moldChangeoverTime = getMoldChangeoverTime(order, bestMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, bestMachine)
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { if (it > 0) it.toLong() else 1L }
                // 连续生产模式：机台在完成一个订单后，经过换模/换管时间即可开始下一个订单
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
