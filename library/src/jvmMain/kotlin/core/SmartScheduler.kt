package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 订单需求数据类
 */
data class OrderRequirement(
    val productionTime: Int,        // 生产时间（小时）
    val moldChangeover: Int,        // 换模时间（小时）
    val pipeChangeover: Int,        // 换管时间（小时）
    val totalTime: Int,             // 总时间（小时）
    val productionQuantity: Int,    // 生产数量
    val assignedMachine: Machine? = null // 分配的机台
)

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
     * 智能排产逻辑
     * 1. 计算每个订单的时间和资源需求（包括换模换管时间）
     * 2. 智能排产，最大化产能利用率
     * 3. 考虑机台规则匹配和产量计算
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
        
        // 步骤1：计算所有订单的时间和资源需求
        val orderRequirements = calculateOrderRequirements(orders)
        println("\n📊 订单需求分析:")
        orderRequirements.forEach { (order, requirement) ->
            println("订单${order.id}: 生产时间=${requirement.productionTime}h, 换模=${requirement.moldChangeover}h, 换管=${requirement.pipeChangeover}h, 总时间=${requirement.totalTime}h")
        }
        
        // 步骤2：智能排产，最大化产能利用率
        val schedulingPlan = createOptimalSchedulingPlan(orderRequirements, machines, machineAvailability)
        
        // 步骤3：执行排产计划
        var processedCount = 0
        var skippedCount = 0
        var scheduledCount = 0
        
        for ((order, requirement) in schedulingPlan) {
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
            
            // 获取分配的机台
            val finalMachine = requirement.assignedMachine
            if (finalMachine == null) {
                println("❌ 订单 ${order.id} 未分配到机台")
                continue
            }
            
            // 智能排产：计算开始时间
            val startDate = machineAvailability[finalMachine.id]!!
            println("智能排产开始时间: $startDate")
            
            if (finalMachine != null) {
                val startDate = machineAvailability[finalMachine.id]!!
                
                // 计算生产时间：(未发订单数-注塑完成) * (日产量/24) 单位是h
                println("订单${order.id} 日产量数据: ${order.dailyProduction}")
                val productionTime = if (order.dailyProduction > 0) {
                    // 生产时间 = 排产数量 * (日产量 / 24)
                    val time = kotlin.math.ceil(productionQuantity.toDouble() * (order.dailyProduction / 24.0)).toInt()
                    println("订单${order.id} 生产时间计算: ${productionQuantity} * (${order.dailyProduction} / 24) = ${time}小时")
                    time
                } else {
                    println("⚠️ 订单${order.id} 日产量为0，使用默认估算")
                    // 如果没有日产量数据，根据产量估算
                    when {
                        productionQuantity <= 10 -> 8   // 小订单8小时
                        productionQuantity <= 50 -> 16  // 中等订单16小时
                        else -> 24 // 大订单24小时
                    }
                }
                
                // 计算生产天数，考虑24小时限制
                val productionDays = kotlin.math.ceil(productionTime / 24.0).toInt()
                val endDate = startDate.plusDays(productionDays.toLong() - 1)
                
                println("✅ 订单 ${order.id} 排产成功")
                println("机台: ${finalMachine.id}, 开始: $startDate, 结束: $endDate")
                println("生产时间: ${productionTime}小时, 生产天数: ${productionDays}天")
                
                val scheduledOrder = order.copy(
                    startDate = startDate,
                    endDate = endDate,
                    status = OrderStatus.IN_PRODUCTION,
                    schedulingStatus = SchedulingStatus.SCHEDULED,
                    machine = finalMachine.id,
                    productionDays = productionDays.toDouble(),
                    remainingDays = productionDays.toDouble(),
                    quantity = productionQuantity
                )
                
                machineSchedule[finalMachine.id]!!.add(scheduledOrder)
                scheduledOrders.add(scheduledOrder)
                scheduledCount++
                
                // 换模换管时间处理：两个操作可以拆开，但需要考虑24小时限制
                val moldChangeoverTime = getMoldChangeoverTime(order, finalMachine)
                val pipeChangeoverTime = getPipeChangeoverTime(order, finalMachine)
                
                println("换模时间: ${moldChangeoverTime}小时, 换管时间: ${pipeChangeoverTime}小时")
                
                // 换模和换管可以并行或错开安排，取最大值
                val totalChangeoverTime = maxOf(moldChangeoverTime, pipeChangeoverTime)
                val changeoverDays = (totalChangeoverTime / 24.0).let { 
                    if (it > 0) kotlin.math.ceil(it).toLong() else 1L 
                }
                
                // 更新机台可用时间，考虑换模换管时间
                machineAvailability[finalMachine.id] = endDate.plusDays(changeoverDays)
                
                println("总换模换管时间: ${totalChangeoverTime}小时 (${changeoverDays}天)")
                
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
     * 计算订单需求
     * 包括生产时间、智能换模时间、智能换管时间
     */
    private fun calculateOrderRequirements(orders: List<ProductionOrder>): Map<ProductionOrder, OrderRequirement> {
        val requirements = mutableMapOf<ProductionOrder, OrderRequirement>()
        
        orders.forEach { order ->
            val productionQuantity = adjustQuantityByPipeStatus(order)
            if (productionQuantity <= 0) return@forEach
            
            // 计算生产时间：(未发订单数-注塑完成) * (日产量/24) 单位是h
            println("订单${order.id} 日产量数据: ${order.dailyProduction}")
            val productionTime = if (order.dailyProduction > 0) {
                // 生产时间 = 排产数量 * (日产量 / 24)
                val time = kotlin.math.ceil(productionQuantity.toDouble() * (order.dailyProduction / 24.0)).toInt()
                println("订单${order.id} 生产时间计算: ${productionQuantity} * (${order.dailyProduction} / 24) = ${time}小时")
                time
            } else {
                println("⚠️ 订单${order.id} 日产量为0，使用默认估算")
                // 如果没有日产量数据，根据产量估算
                when {
                    productionQuantity <= 10 -> 8   // 小订单8小时
                    productionQuantity <= 50 -> 16  // 中等订单16小时
                    else -> 24 // 大订单24小时
                }
            }
            
            // 智能换模换管时间：初始设为0，在排产时根据机台状态动态计算
            val moldChangeover = 0  // 将在排产时根据机台当前模具状态决定
            val pipeChangeover = 0  // 将在排产时根据机台当前管子状态决定
            
            // 总时间 = 生产时间（换模换管时间将在排产时动态添加）
            val totalTime = productionTime
            
            requirements[order] = OrderRequirement(
                productionTime = productionTime,
                moldChangeover = moldChangeover,
                pipeChangeover = pipeChangeover,
                totalTime = totalTime,
                productionQuantity = productionQuantity
            )
        }
        
        return requirements
    }
    
    /**
     * 创建最优排产计划
     * 最大化产能利用率，智能计算开始时间，智能换模换管
     */
    private fun createOptimalSchedulingPlan(
        orderRequirements: Map<ProductionOrder, OrderRequirement>,
                                    machines: List<Machine>,
        machineAvailability: MutableMap<String, LocalDate>
    ): Map<ProductionOrder, OrderRequirement> {
        
        val schedulingPlan = mutableMapOf<ProductionOrder, OrderRequirement>()
        val machineWorkload = mutableMapOf<String, Int>() // 机台工作负载（小时）
        val machineSchedule = mutableMapOf<String, MutableList<OrderRequirement>>() // 机台排产计划
        
        // 机台状态跟踪：记录每个机台当前的模具和管子状态
        val machineMoldState = mutableMapOf<String, String?>() // 机台当前模具ID
        val machinePipeState = mutableMapOf<String, String?>() // 机台当前管子ID
        
        // 初始化机台工作负载、排产计划和状态
        machines.forEach { machine ->
            machineWorkload[machine.id] = 0
            machineSchedule[machine.id] = mutableListOf()
            machineMoldState[machine.id] = null // 初始状态：无模具
            machinePipeState[machine.id] = null // 初始状态：无管子
        }
        
        // 按优先级和总时间排序订单
        val sortedOrders = orderRequirements.entries.sortedWith(compareBy<Map.Entry<ProductionOrder, OrderRequirement>> { 
            it.key.priority.ordinal 
        }.thenBy { 
            it.value.totalTime 
        })
        
        for ((order, requirement) in sortedOrders) {
            // 找到最适合的机台
            val bestMachine = findBestMachineForOrder(order, machines, machineWorkload)
            
            if (bestMachine != null) {
                // 智能换模换管计算
                val (moldChangeover, pipeChangeover) = calculateSmartChangeover(
                    order, bestMachine, machineMoldState, machinePipeState
                )
                
                // 计算总时间：生产时间 + 智能换模时间 + 智能换管时间
                val totalTime = requirement.productionTime + moldChangeover + pipeChangeover
                
                // 计算开始时间：机台当前可用时间
                val startDate = machineAvailability[bestMachine.id]!!
                
                // 计算结束时间：开始时间 + 总时间
                val totalDays = kotlin.math.ceil(totalTime / 24.0).toInt()
                val endDate = startDate.plusDays(totalDays.toLong() - 1)
                
                // 创建更新后的需求，包含智能换模换管时间
                val updatedRequirement = requirement.copy(
                    assignedMachine = bestMachine,
                    moldChangeover = moldChangeover,
                    pipeChangeover = pipeChangeover,
                    totalTime = totalTime
                )
                schedulingPlan[order] = updatedRequirement
                
                // 更新机台工作负载和可用时间
                machineWorkload[bestMachine.id] = machineWorkload[bestMachine.id]!! + totalTime
                machineAvailability[bestMachine.id] = endDate.plusDays(1) // 下个订单从结束时间的下一天开始
                
                // 更新机台状态：记录新的模具和管子
                updateMachineState(order, bestMachine, machineMoldState, machinePipeState)
                
                // 添加到机台排产计划
                machineSchedule[bestMachine.id]!!.add(updatedRequirement)
                
                println("✅ 订单${order.id} 分配到机台${bestMachine.id}")
                println("  开始时间: $startDate, 结束时间: $endDate")
                println("  生产时间: ${requirement.productionTime}小时, 换模: ${moldChangeover}小时, 换管: ${pipeChangeover}小时, 总时间: ${totalTime}小时")
            } else {
                println("❌ 订单${order.id} 无法分配到任何机台")
            }
        }
        
        return schedulingPlan
    }
    
    /**
     * 智能计算换模换管时间
     * 根据机台当前状态和订单需求决定是否需要换模换管
     */
    private fun calculateSmartChangeover(
        order: ProductionOrder,
        machine: Machine,
        machineMoldState: Map<String, String?>,
        machinePipeState: Map<String, String?>
    ): Pair<Int, Int> {
        val currentMold = machineMoldState[machine.id]
        val currentPipe = machinePipeState[machine.id]
        
        // 获取订单需要的模具和管子信息
        val orderMold = getOrderMoldId(order)
        val orderPipe = getOrderPipeId(order)
        
        // 计算换模时间：如果模具不同，需要12小时换模
        val moldChangeover = if (currentMold != orderMold) {
            println("  🔄 机台${machine.id} 需要换模: $currentMold -> $orderMold (12小时)")
            12
        } else {
            println("  ✅ 机台${machine.id} 模具相同: $currentMold (0小时)")
            0
        }
        
        // 计算换管时间：如果管子不同，需要4小时换管
        val pipeChangeover = if (currentPipe != orderPipe) {
            println("  🔄 机台${machine.id} 需要换管: $currentPipe -> $orderPipe (4小时)")
            4
        } else {
            println("  ✅ 机台${machine.id} 管子相同: $currentPipe (0小时)")
            0
        }
        
        return Pair(moldChangeover, pipeChangeover)
    }
    
    /**
     * 更新机台状态
     * 记录机台当前使用的模具和管子
     */
    private fun updateMachineState(
        order: ProductionOrder,
        machine: Machine,
        machineMoldState: MutableMap<String, String?>,
        machinePipeState: MutableMap<String, String?>
    ) {
        val orderMold = getOrderMoldId(order)
        val orderPipe = getOrderPipeId(order)
        
        machineMoldState[machine.id] = orderMold
        machinePipeState[machine.id] = orderPipe
        
        println("  📝 机台${machine.id} 状态更新: 模具=$orderMold, 管子=$orderPipe")
    }
    
    /**
     * 获取订单对应的模具ID
     * 根据机台规则匹配获取模具信息
     */
    private fun getOrderMoldId(order: ProductionOrder): String {
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        return assignment?.moldId ?: "UNKNOWN"
    }
    
    /**
     * 获取订单对应的管子ID
     * 根据机台规则匹配获取管子信息
     */
    private fun getOrderPipeId(order: ProductionOrder): String {
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        // 由于MachineAssignment没有pipeId字段，我们使用订单的管规格作为管子标识
        return "${order.innerDiameter}x${order.outerDiameter}"
    }
    
    /**
     * 为订单找到最佳机台
     * 基于机台规则匹配，考虑工作负载平衡
     */
    private fun findBestMachineForOrder(
        order: ProductionOrder,
                                      machines: List<Machine>,
        machineWorkload: Map<String, Int>
    ): Machine? {
        
        println("  🔍 为订单${order.id} 寻找最佳机台...")
        
        // 使用机台分配引擎获取匹配的机台
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        if (assignment != null) {
            val targetMachineId = assignment.machineId
            println("  📋 机台规则匹配: 订单${order.id} -> 机台${targetMachineId}")
            
            // 在可用机台中查找匹配的机台
            val matchedMachine = machines.find { it.id == targetMachineId }
            if (matchedMachine != null) {
                println("  ✅ 找到匹配机台: ${matchedMachine.id}")
                return matchedMachine
            } else {
                println("  ⚠️ 匹配的机台${targetMachineId}不在可用机台列表中")
            }
        } else {
            println("  ❌ 机台规则无法匹配订单${order.id}")
        }
        
        // 如果没有精确匹配，使用备用机台分配策略
        println("  🔄 使用备用机台分配策略...")
        return findFallbackMachine(order, machines, mutableMapOf())
    }
    
    /**
     * 检查机台是否能处理订单
     * 基于机台规则匹配
     */
    private fun canMachineHandleOrder(order: ProductionOrder, machine: Machine): Boolean {
        val machineAssignmentEngine = MachineAssignmentEngine()
        val machineRules = machineAssignmentEngine.createDefaultMachineRules()
        val assignment = machineAssignmentEngine.assignMachine(order, machineRules)
        
        // 检查机台是否匹配
        return assignment?.machineId == machine.id
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