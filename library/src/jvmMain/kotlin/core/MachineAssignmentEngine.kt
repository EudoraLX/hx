package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate

/**
 * 机台分配引擎
 * 负责根据订单规格和机台规则分配最佳机台
 * 根据机台划分规则表进行精确匹配
 */
class MachineAssignmentEngine {
    private val pipeSpecParser = PipeSpecParser()
    
    /**
     * 为订单分配最佳机台
     * 根据机台划分规则表进行精确匹配
     */
    fun assignMachine(order: ProductionOrder, machineRules: List<MachineRule>): MachineAssignment? {
        val orderInnerDiameter = order.innerDiameter
        val orderOuterDiameter = order.outerDiameter
        
        println("    🔍 机台分配引擎 - 订单${order.id}: 内径=${orderInnerDiameter}, 外径=${orderOuterDiameter}")
        
        // 根据机台划分规则表进行精确匹配
        val matchingRule = findBestMatchingRule(orderInnerDiameter, orderOuterDiameter, machineRules)
        
        if (matchingRule != null) {
            println("    ✅ 找到匹配规则: 机台${matchingRule.machineId}, 模具${matchingRule.moldId}")
            
            val assignment = MachineAssignment(
                machineId = matchingRule.machineId,
                moldId = matchingRule.moldId,
                changeoverTime = matchingRule.changeoverTime,
                pipeChangeTime = matchingRule.pipeChangeTime,
                totalSetupTime = matchingRule.changeoverTime + matchingRule.pipeChangeTime,
                cost = calculateAssignmentCost(order, matchingRule)
            )
            
            println("    ✅ 机台分配成功: ${assignment.machineId}, 模具: ${assignment.moldId}, 成本: ${assignment.cost}")
            return assignment
        } else {
            println("    ❌ 未找到匹配的机台规则")
            return null
        }
    }
    
    /**
     * 根据机台划分规则表查找最佳匹配规则
     */
    private fun findBestMatchingRule(innerDiameter: Double, outerDiameter: Double, machineRules: List<MachineRule>): MachineRule? {
        // 优先匹配外径，因为外径是主要决定因素
        val outerDiameterMatches = machineRules.filter { rule ->
            rule.outerDiameters.contains(outerDiameter)
        }
        
        if (outerDiameterMatches.isNotEmpty()) {
            // 如果外径匹配，再检查内径
            val innerDiameterMatches = outerDiameterMatches.filter { rule ->
                rule.innerDiameters.contains(innerDiameter) || rule.innerDiameters.contains(0.0)
            }
            
            if (innerDiameterMatches.isNotEmpty()) {
                // 返回第一个匹配的规则（可以根据需要调整优先级）
                return innerDiameterMatches.first()
            } else {
                // 如果内径不匹配，但外径匹配，仍然可以使用（内径为0表示不限制）
                return outerDiameterMatches.first()
            }
        }
        
        // 如果没有精确匹配，尝试范围匹配
        return findRangeMatchingRule(innerDiameter, outerDiameter, machineRules)
    }
    
    /**
     * 范围匹配规则
     */
    private fun findRangeMatchingRule(innerDiameter: Double, outerDiameter: Double, machineRules: List<MachineRule>): MachineRule? {
        // 根据外径范围进行匹配
        return when {
            outerDiameter <= 150 -> machineRules.find { it.machineId == "2#" }
            outerDiameter in 160.0..218.0 -> machineRules.find { it.machineId == "3#" }
            outerDiameter in 250.0..272.0 || outerDiameter == 414.0 -> machineRules.find { it.machineId == "4#" }
            outerDiameter in 290.0..400.0 -> machineRules.find { it.machineId == "5#" }
            outerDiameter == 280.0 || outerDiameter == 510.0 -> machineRules.find { it.machineId == "6#" }
            outerDiameter == 600.0 -> machineRules.find { it.machineId == "7#" }
            else -> null
        }
    }
    
    /**
     * 计算分配成本
     */
    private fun calculateAssignmentCost(order: ProductionOrder, rule: MachineRule): Double {
        val setupCost = (rule.changeoverTime + rule.pipeChangeTime) * 10.0
        val productionCost = order.calculateProductionDays() * 5.0
        val priorityCost = when (order.priority) {
            OrderPriority.URGENT -> -50.0
            OrderPriority.HIGH -> -20.0
            OrderPriority.MEDIUM -> 0.0
            OrderPriority.LOW -> 10.0
        }
        
        return setupCost + productionCost + priorityCost
    }
    
    /**
     * 创建默认机台规则
     * 根据机台划分规则表更新
     */
    fun createDefaultMachineRules(): List<MachineRule> {
        return listOf(
            // 机台1# - MC-003-GN-2012
            MachineRule(
                machineId = "1#",
                moldId = "MC-003-GN-2012",
                pipeSpecs = emptyList(),
                description = "机台1#规则",
                innerDiameters = listOf(130.0, 204.0),
                outerDiameters = listOf(154.0, 226.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台1# - MC-007-H&G-2019
            MachineRule(
                machineId = "1#",
                moldId = "MC-007-H&G-2019",
                pipeSpecs = emptyList(),
                description = "Ø174外径和两个小锥",
                innerDiameters = listOf(102.0, 195.0),
                outerDiameters = listOf(122.0, 215.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台1# - MC-030-H&G-2022
            MachineRule(
                machineId = "1#",
                moldId = "MC-030-H&G-2022",
                pipeSpecs = emptyList(),
                description = "大规格",
                innerDiameters = listOf(102.0, 195.0),
                outerDiameters = listOf(122.0, 215.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台1# - M-013-H&G-2020
            MachineRule(
                machineId = "1#",
                moldId = "M-013-H&G-2020",
                pipeSpecs = emptyList(),
                description = "机台1#规则",
                innerDiameters = listOf(140.0, 154.0, 160.0),
                outerDiameters = listOf(174.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台2# - M-008-H&G-2019
            MachineRule(
                machineId = "2#",
                moldId = "M-008-H&G-2019",
                pipeSpecs = emptyList(),
                description = "Ø150外径及以下",
                innerDiameters = listOf(100.0, 113.0, 120.0),
                outerDiameters = listOf(137.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台2# - M-019-H&G-2020
            MachineRule(
                machineId = "2#",
                moldId = "M-019-H&G-2020",
                pipeSpecs = emptyList(),
                description = "Ø150外径及以下",
                innerDiameters = listOf(0.0),
                outerDiameters = listOf(90.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台2# - M-020-H&G-2020
            MachineRule(
                machineId = "2#",
                moldId = "M-020-H&G-2020",
                pipeSpecs = emptyList(),
                description = "Ø150外径及以下",
                innerDiameters = listOf(0.0),
                outerDiameters = listOf(110.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台2# - M-021-H&G-2020
            MachineRule(
                machineId = "2#",
                moldId = "M-021-H&G-2020",
                pipeSpecs = emptyList(),
                description = "Ø150外径及以下",
                innerDiameters = listOf(0.0),
                outerDiameters = listOf(120.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台2# - M-022-H&G-2020
            MachineRule(
                machineId = "2#",
                moldId = "M-022-H&G-2020",
                pipeSpecs = emptyList(),
                description = "Ø150外径及以下",
                innerDiameters = listOf(130.0),
                outerDiameters = listOf(147.0, 150.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台3# - M-005-H&G-2018
            MachineRule(
                machineId = "3#",
                moldId = "M-005-H&G-2018",
                pipeSpecs = emptyList(),
                description = "Ø160~Ø218外径",
                innerDiameters = listOf(125.0, 130.0, 140.0),
                outerDiameters = listOf(160.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台3# - M-039-H&G-2025
            MachineRule(
                machineId = "3#",
                moldId = "M-039-H&G-2025",
                pipeSpecs = emptyList(),
                description = "Ø160~Ø218外径",
                innerDiameters = listOf(180.0, 200.0),
                outerDiameters = listOf(217.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台3# - M-004-H&G-2018
            MachineRule(
                machineId = "3#",
                moldId = "M-004-H&G-2018",
                pipeSpecs = emptyList(),
                description = "Ø160~Ø218外径",
                innerDiameters = listOf(180.0, 200.0),
                outerDiameters = listOf(218.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台3# - M-018-H&G-2021
            MachineRule(
                machineId = "3#",
                moldId = "M-018-H&G-2021",
                pipeSpecs = emptyList(),
                description = "Ø160~Ø218外径",
                innerDiameters = listOf(182.0),
                outerDiameters = listOf(200.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台4# - M-011-H&G-2019
            MachineRule(
                machineId = "4#",
                moldId = "M-011-H&G-2019",
                pipeSpecs = emptyList(),
                description = "Ø250~Ø272、Ø414外径 机动5#",
                innerDiameters = listOf(220.0, 230.0),
                outerDiameters = listOf(250.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台4# - M-010-H&G-2019
            MachineRule(
                machineId = "4#",
                moldId = "M-010-H&G-2019",
                pipeSpecs = emptyList(),
                description = "Ø250~Ø272、Ø414外径 机动5#",
                innerDiameters = listOf(240.0),
                outerDiameters = listOf(260.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台4# - M-033-H&G-2022
            MachineRule(
                machineId = "4#",
                moldId = "M-033-H&G-2022",
                pipeSpecs = emptyList(),
                description = "Ø250~Ø272、Ø414外径 机动5#",
                innerDiameters = listOf(248.0),
                outerDiameters = listOf(272.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台4# - M-015-H&G-2021
            MachineRule(
                machineId = "4#",
                moldId = "M-015-H&G-2021",
                pipeSpecs = emptyList(),
                description = "Ø250~Ø272、Ø414外径 机动5#",
                innerDiameters = listOf(400.0),
                outerDiameters = listOf(414.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台5# - M-035-H&G-2022
            MachineRule(
                machineId = "5#",
                moldId = "M-035-H&G-2022",
                pipeSpecs = emptyList(),
                description = "Ø290~Ø400外径 机动4#",
                innerDiameters = listOf(270.0),
                outerDiameters = listOf(290.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台5# - M-032-H&G-2022
            MachineRule(
                machineId = "5#",
                moldId = "M-032-H&G-2022",
                pipeSpecs = emptyList(),
                description = "Ø290~Ø400外径 机动4#",
                innerDiameters = listOf(300.0),
                outerDiameters = listOf(320.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台5# - M-006-H&G-2018
            MachineRule(
                machineId = "5#",
                moldId = "M-006-H&G-2018",
                pipeSpecs = emptyList(),
                description = "Ø290~Ø400外径 机动4#",
                innerDiameters = listOf(350.0, 375.0),
                outerDiameters = listOf(400.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台6# - MC-036-H&G-2024
            MachineRule(
                machineId = "6#",
                moldId = "MC-036-H&G-2024",
                pipeSpecs = emptyList(),
                description = "Ø280、Ø510外径和两个大锥",
                innerDiameters = listOf(180.0, 330.0),
                outerDiameters = listOf(198.0, 348.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台6# - MC-017-H&G-2021
            MachineRule(
                machineId = "6#",
                moldId = "MC-017-H&G-2021",
                pipeSpecs = emptyList(),
                description = "Ø280、Ø510外径和两个大锥",
                innerDiameters = listOf(180.0, 360.0),
                outerDiameters = listOf(200.0, 380.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台6# - M-014-H&G-2021
            MachineRule(
                machineId = "6#",
                moldId = "M-014-H&G-2021",
                pipeSpecs = emptyList(),
                description = "Ø280、Ø510外径和两个大锥",
                innerDiameters = listOf(0.0),
                outerDiameters = listOf(280.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台6# - M-009-H&G-2019
            MachineRule(
                machineId = "6#",
                moldId = "M-009-H&G-2019",
                pipeSpecs = emptyList(),
                description = "Ø280、Ø510外径和两个大锥",
                innerDiameters = listOf(482.0),
                outerDiameters = listOf(510.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            ),
            
            // 机台7# - M-037-H&G-2024
            MachineRule(
                machineId = "7#",
                moldId = "M-037-H&G-2024",
                pipeSpecs = emptyList(),
                description = "Ø600外径",
                innerDiameters = listOf(600.0),
                outerDiameters = listOf(625.0),
                changeoverTime = 12,
                pipeChangeTime = 4
            )
        )
    }
}