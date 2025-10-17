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
     */
    private fun capacityFirstScheduling(orders: List<ProductionOrder>, 
                                      machines: List<Machine>,
                                      constraints: SchedulingConstraints): SchedulingResult {
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { it.quantity }.reversed())
        val machineSchedule = mutableMapOf<String, MutableList<ProductionOrder>>()
        val scheduledOrders = mutableListOf<ProductionOrder>()
        val conflicts = mutableListOf<String>()
        
        // 初始化机台排产计划
        machines.forEach { machine ->
            machineSchedule[machine.id] = mutableListOf()
        }
        
        var currentDate = LocalDate.now()
        
        for (order in sortedOrders) {
            val bestMachine = findBestMachineForOrder(order, machines, machineSchedule, currentDate, constraints)
            
            if (bestMachine != null) {
                val productionDays = order.calculateProductionDays()
                val startDate = currentDate
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    machine = bestMachine.id
                )
                
                machineSchedule[bestMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                
                // 更新当前日期到下一个可用时间
                currentDate = endDate.plusDays(1)
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
     * 时间优先排产
     */
    private fun timeFirstScheduling(orders: List<ProductionOrder>, 
                                   machines: List<Machine>,
                                   constraints: SchedulingConstraints): SchedulingResult {
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { 
            it.plannedDeliveryDate ?: LocalDate.MAX 
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
     */
    private fun orderFirstScheduling(orders: List<ProductionOrder>, 
                                    machines: List<Machine>,
                                    constraints: SchedulingConstraints): SchedulingResult {
        val sortedOrders = orders.sortedWith(compareBy<ProductionOrder> { 
            when (it.priority) {
                OrderPriority.URGENT -> 0
                OrderPriority.HIGH -> 1
                OrderPriority.MEDIUM -> 2
                OrderPriority.LOW -> 3
            }
        }.thenBy { it.plannedDeliveryDate ?: LocalDate.MAX })
        
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
        // 综合考虑产能、时间、订单优先级
        val weightedOrders = orders.map { order ->
            val priorityWeight = when (order.priority) {
                OrderPriority.URGENT -> 4.0
                OrderPriority.HIGH -> 3.0
                OrderPriority.MEDIUM -> 2.0
                OrderPriority.LOW -> 1.0
            }
            
            val urgencyWeight = if (order.isUrgent()) 2.0 else 1.0
            val quantityWeight = order.quantity.toDouble() / (orders.maxOfOrNull { it.quantity }?.toDouble() ?: 1.0)
            
            val totalWeight = priorityWeight * urgencyWeight * quantityWeight
            
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
