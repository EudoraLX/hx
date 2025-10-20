package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate

/**
 * 机台分配引擎
 * 负责根据订单规格和机台规则分配最佳机台
 */
class MachineAssignmentEngine {
    private val pipeSpecParser = PipeSpecParser()
    
    /**
     * 为订单分配最佳机台
     */
    fun assignMachine(order: ProductionOrder, machineRules: List<MachineRule>): MachineAssignment? {
        val orderSpec = PipeSpecification(
            innerDiameter = order.innerDiameter,
            outerDiameter = order.outerDiameter
        )
        
        // 1. 查找匹配的机台规则
        val matchingRules = machineRules.filter { rule ->
            rule.pipeSpecs.any { spec -> 
                pipeSpecParser.isPipeSpecMatch(orderSpec, spec)
            }
        }
        
        if (matchingRules.isEmpty()) {
            return null
        }
        
        // 2. 考虑机台互换性
        val availableMachines = expandInterchangeableMachines(matchingRules)
        
        // 3. 计算成本并选择最佳机台
        return findOptimalMachine(order, availableMachines)
    }
    
    /**
     * 扩展可互换机台
     */
    private fun expandInterchangeableMachines(rules: List<MachineRule>): List<MachineRule> {
        val expanded = mutableListOf<MachineRule>()
        expanded.addAll(rules)
        
        // 处理机台互换性
        rules.forEach { rule ->
            rule.interchangeableWith.forEach { interchangeableId ->
                val interchangeableRule = rules.find { it.machineId == interchangeableId }
                if (interchangeableRule != null && !expanded.contains(interchangeableRule)) {
                    expanded.add(interchangeableRule)
                }
            }
        }
        
        return expanded
    }
    
    /**
     * 找到最佳机台
     */
    private fun findOptimalMachine(order: ProductionOrder, availableMachines: List<MachineRule>): MachineAssignment? {
        if (availableMachines.isEmpty()) return null
        
        var bestAssignment: MachineAssignment? = null
        var minCost = Double.MAX_VALUE
        
        for (rule in availableMachines) {
            val assignment = calculateMachineAssignment(order, rule)
            if (assignment.cost < minCost) {
                minCost = assignment.cost
                bestAssignment = assignment
            }
        }
        
        return bestAssignment
    }
    
    /**
     * 计算机台分配成本
     */
    private fun calculateMachineAssignment(order: ProductionOrder, rule: MachineRule): MachineAssignment {
        val changeoverTime = rule.changeoverTime
        val pipeChangeTime = rule.pipeChangeTime
        val totalSetupTime = changeoverTime + pipeChangeTime
        
        // 计算成本：换模时间 + 换管时间 + 等待时间
        val setupCost = totalSetupTime * 10.0 // 每小时成本
        val productionCost = order.calculateProductionDays() * 5.0 // 生产天数成本
        val priorityCost = when (order.priority) {
            OrderPriority.URGENT -> -50.0 // 紧急订单降低成本
            OrderPriority.HIGH -> -20.0
            OrderPriority.MEDIUM -> 0.0
            OrderPriority.LOW -> 10.0
        }
        
        val totalCost = setupCost + productionCost + priorityCost
        
        return MachineAssignment(
            machineId = rule.machineId,
            moldId = rule.moldId,
            changeoverTime = changeoverTime,
            pipeChangeTime = pipeChangeTime,
            totalSetupTime = totalSetupTime,
            cost = totalCost
        )
    }
    
    /**
     * 创建默认机台规则
     */
    fun createDefaultMachineRules(): List<MachineRule> {
        val pipeSpecParser = PipeSpecParser()
        
        return listOf(
            // 机台1#
            MachineRule(
                machineId = "1#",
                moldId = "MC-003-GN-2012",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 130/154-Ø 204/226"),
                description = "机台1#规则",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "1#",
                moldId = "MC-007-H&G-2019",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 102/Ø 122-Ø 195/Ø 215"),
                description = "Ø 174外径和两个小锥",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "1#",
                moldId = "MC-030-H&G-2022",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 102/Ø 122-Ø 195/Ø 215 (大)"),
                description = "大规格",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "1#",
                moldId = "M-013-H&G-2020",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 140、Ø 154、Ø 160/Ø 174"),
                description = "机台1#规则",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            
            // 机台2#
            MachineRule(
                machineId = "2#",
                moldId = "M-008-H&G-2019",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 100、Ø 113、Ø 120/Ø 137"),
                description = "Ø 150外径及以下",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "2#",
                moldId = "M-019-H&G-2020",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 90"),
                description = "Ø 150外径及以下",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "2#",
                moldId = "M-020-H&G-2020",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 110"),
                description = "Ø 150外径及以下",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "2#",
                moldId = "M-021-H&G-2020",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 120"),
                description = "Ø 150外径及以下",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "2#",
                moldId = "M-022-H&G-2020",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 130/Ø 147、Ø 150"),
                description = "Ø 150外径及以下",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            
            // 机台3#
            MachineRule(
                machineId = "3#",
                moldId = "M-005-H&G-2018",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 125、Ø 130、Ø 140/Ø 160"),
                description = "Ø 160~Ø 218外径",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "3#",
                moldId = "M-039-H&G-2025",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 180、Ø 200/Ø 217 (大)"),
                description = "Ø 160~Ø 218外径",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "3#",
                moldId = "M-004-H&G-2018",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 180、Ø 200/Ø 218"),
                description = "Ø 160~Ø 218外径",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "3#",
                moldId = "M-018-H&G-2021",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 182/Ø 200"),
                description = "Ø 160~Ø 218外径",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            
            // 机台4# (可互换5#)
            MachineRule(
                machineId = "4#",
                moldId = "M-011-H&G-2019",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 220、Ø 230/Ø 250"),
                description = "Ø 250~Ø 272、Ø 414外径 机动5#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("5#")
            ),
            MachineRule(
                machineId = "4#",
                moldId = "M-010-H&G-2019",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 240/Ø 260"),
                description = "Ø 250~Ø 272、Ø 414外径 机动5#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("5#")
            ),
            MachineRule(
                machineId = "4#",
                moldId = "M-033-H&G-2022",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 248/Ø 272"),
                description = "Ø 250~Ø 272、Ø 414外径 机动5#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("5#")
            ),
            MachineRule(
                machineId = "4#",
                moldId = "M-015-H&G-2021",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 400/Ø 414"),
                description = "Ø 250~Ø 272、Ø 414外径 机动5#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("5#")
            ),
            
            // 机台5# (可互换4#)
            MachineRule(
                machineId = "5#",
                moldId = "M-035-H&G-2022",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 270/Ø 290"),
                description = "Ø 290~Ø 400外径 机动4#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("4#")
            ),
            MachineRule(
                machineId = "5#",
                moldId = "M-032-H&G-2022",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 300/Ø 320"),
                description = "Ø 290~Ø 400外径 机动4#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("4#")
            ),
            MachineRule(
                machineId = "5#",
                moldId = "M-006-H&G-2018",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 350、Ø 375/Ø 400"),
                description = "Ø 290~Ø 400外径 机动4#",
                changeoverTime = 4,
                pipeChangeTime = 12,
                interchangeableWith = listOf("4#")
            ),
            
            // 机台6#
            MachineRule(
                machineId = "6#",
                moldId = "MC-036-H&G-2024",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 180/Ø 198-Ø 330/Ø 348"),
                description = "Ø 280、Ø 510外径和两个大锥",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "6#",
                moldId = "MC-017-H&G-2021",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 180/200-Ø 360/380"),
                description = "Ø 280、Ø 510外径和两个大锥",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "6#",
                moldId = "M-014-H&G-2021",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 280"),
                description = "Ø 280、Ø 510外径和两个大锥",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            MachineRule(
                machineId = "6#",
                moldId = "M-009-H&G-2019",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 482/Ø 510"),
                description = "Ø 280、Ø 510外径和两个大锥",
                changeoverTime = 4,
                pipeChangeTime = 12
            ),
            
            // 机台7#
            MachineRule(
                machineId = "7#",
                moldId = "M-037-H&G-2024",
                pipeSpecs = pipeSpecParser.parsePipeSpecs("Ø 600/Ø 625"),
                description = "Ø 600外径",
                changeoverTime = 4,
                pipeChangeTime = 12
            )
        )
    }
}
