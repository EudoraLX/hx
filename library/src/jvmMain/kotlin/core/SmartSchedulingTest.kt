package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate

/**
 * 智能排产系统测试
 */
class SmartSchedulingTest {
    
    fun testPipeSpecParser() {
        println("=== 测试管规格解析器 ===")
        val parser = PipeSpecParser()
        
        // 测试各种格式
        val testSpecs = listOf(
            "Ø 130/154-Ø 204/226",
            "Ø 102、Ø 113、Ø 120/Ø 137",
            "Ø 90",
            "Ø 180、Ø 200/Ø 217 (大)"
        )
        
        testSpecs.forEach { spec ->
            println("解析规格: $spec")
            val parsed = parser.parsePipeSpecs(spec)
            parsed.forEach { pipeSpec ->
                println("  内径: ${pipeSpec.innerDiameter}, 外径: ${pipeSpec.outerDiameter}, 大规格: ${pipeSpec.isLarge}")
            }
            println()
        }
    }
    
    fun testMachineAssignment() {
        println("=== 测试机台分配 ===")
        val engine = MachineAssignmentEngine()
        
        // 创建测试订单
        val testOrder = ProductionOrder(
            id = "001",
            companyModel = "FH972213",
            customerModel = "FH-550/12.5-4295",
            plannedDeliveryDate = LocalDate.now().plusDays(10),
            plannedQuantity = 100,
            quantity = 100,
            deliveryPeriod = LocalDate.now().plusDays(15),
            innerDiameter = 12.5,
            outerDiameter = 550.0,
            dailyProduction = 10,
            productionDays = 10.0,
            remainingDays = 10.0,
            shippedQuantity = 0,
            unshippedQuantity = 100,
            machine = "",
            pipeStatus = "",
            isCompleted = false,
            priority = OrderPriority.HIGH,
            status = OrderStatus.PENDING
        )
        
        val machineRules = engine.createDefaultMachineRules()
        val assignment = engine.assignMachine(testOrder, machineRules)
        
        if (assignment != null) {
            println("订单 ${testOrder.id} 分配到机台: ${assignment.machineId}")
            println("模具: ${assignment.moldId}")
            println("换模时间: ${assignment.changeoverTime}小时")
            println("换管时间: ${assignment.pipeChangeTime}小时")
            println("总设置时间: ${assignment.totalSetupTime}小时")
            println("成本: ${assignment.cost}")
        } else {
            println("未找到合适的机台")
        }
        println()
    }
    
    fun testOrderFilter() {
        println("=== 测试订单筛选 ===")
        val filter = OrderFilter()
        
        // 创建测试订单列表
        val testOrders = listOf(
            ProductionOrder(
                id = "001", 
                companyModel = "FH001", 
                plannedDeliveryDate = LocalDate.now().plusDays(5),
                plannedQuantity = 100, 
                shippedQuantity = 50,
                unshippedQuantity = 50, 
                pipeStatus = "", 
                isCompleted = false,
                priority = OrderPriority.HIGH, 
                status = OrderStatus.PENDING,
                dailyProduction = 10, 
                productionDays = 10.0, 
                remainingDays = 5.0,
                quantity = 100, 
                deliveryPeriod = LocalDate.now().plusDays(10),
                innerDiameter = 100.0, 
                outerDiameter = 150.0, 
                machine = "1#"
            ),
            ProductionOrder(
                id = "002", 
                companyModel = "FH002", 
                plannedDeliveryDate = LocalDate.now().plusDays(3),
                plannedQuantity = 100, 
                shippedQuantity = 100,
                unshippedQuantity = 0, 
                pipeStatus = "已完成", 
                isCompleted = true,
                priority = OrderPriority.LOW, 
                status = OrderStatus.COMPLETED,
                dailyProduction = 10, 
                productionDays = 10.0, 
                remainingDays = 0.0,
                quantity = 100, 
                deliveryPeriod = LocalDate.now().plusDays(10),
                innerDiameter = 100.0, 
                outerDiameter = 150.0, 
                machine = "2#"
            )
        )
        
        val incompleteOrders = testOrders.filter { order ->
            !order.isCompleted && order.unshippedQuantity > 0
        }
        
        println("总订单数: ${testOrders.size}")
        println("未完成订单数: ${incompleteOrders.size}")
        incompleteOrders.forEach { order ->
            println("  订单 ${order.id}: 未发数量 ${order.unshippedQuantity}")
        }
        println()
    }
    
    fun runAllTests() {
        testPipeSpecParser()
        testMachineAssignment()
        testOrderFilter()
        println("=== 所有测试完成 ===")
    }
}

fun main() {
    val test = SmartSchedulingTest()
    test.runAllTests()
}
