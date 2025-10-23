package io.github.kotlin.fibonacci.model

import java.time.LocalDate

/**
 * 生产订单数据模型
 */
data class ProductionOrder(
    val id: String,                    // 序号
    val companyModel: String,          // 公司型号
    val customerModel: String = "",    // 客户型号
    val customerName: String = "",     // 客户名称
    val plannedDeliveryDate: LocalDate?, // 计划发货时间
    val plannedQuantity: Int,          // 计划发货数量
    val quantity: Int,                 // 数量（支）
    val segments: Int = 1,             // 段数
    val deliveryPeriod: LocalDate?,    // 交付期
    val innerDiameter: Double = 0.0,   // 内径
    val outerDiameter: Double = 0.0,   // 外径
    val dailyProduction: Int,          // 日产量
    val productionDays: Double,        // 生产天数
    val remainingDays: Double,         // 剩余天数
    val shippedQuantity: Int,          // 已发货数
    val unshippedQuantity: Int,        // 未发数量
    val machine: String,               // 机台
    val pipeStatus: String = "",       // 管子情况
    val pipeQuantity: Int = 0,         // 管子数量
    val pipeArrivalDate: LocalDate? = null, // 管子到货日期
    val injectionCompleted: Int? = null, // 注塑完成数量
    val isCompleted: Boolean = false,   // 是否已完成
    val priority: OrderPriority,       // 订单优先级
    val status: OrderStatus,           // 订单状态
    val startDate: LocalDate? = null,  // 计划开始日期
    val endDate: LocalDate? = null,    // 计划完成日期
    val notes: String = ""             // 备注
) {
    /**
     * 计算生产所需天数
     */
    fun calculateProductionDays(): Int {
        return if (dailyProduction > 0) {
            (unshippedQuantity.toDouble() / dailyProduction).toInt() + 1
        } else {
            productionDays.toInt()
        }
    }
    
    /**
     * 检查是否已完成
     */
    fun checkIfCompleted(): Boolean {
        return plannedQuantity > 0 && plannedQuantity == shippedQuantity
    }
    
    /**
     * 检查是否紧急
     */
    fun isUrgent(): Boolean {
        return priority == OrderPriority.URGENT || 
               (plannedDeliveryDate != null && 
                plannedDeliveryDate.isBefore(LocalDate.now().plusDays(3)))
    }
    
    /**
     * 检查管子数量是否足够
     */
    fun hasEnoughPipeQuantity(): Boolean {
        return pipeQuantity >= quantity * segments
    }
    
    /**
     * 检查管子是否已到货
     */
    fun isPipeArrived(): Boolean {
        return pipeArrivalDate != null && pipeArrivalDate.isBefore(LocalDate.now()) || pipeArrivalDate == null
    }
}

/**
 * 订单优先级枚举
 */
enum class OrderPriority {
    URGENT,    // 紧急
    HIGH,      // 高
    MEDIUM,    // 中
    LOW        // 低
}

/**
 * 订单状态枚举
 */
enum class OrderStatus {
    PENDING,      // 待排产
    IN_PRODUCTION, // 生产中
    COMPLETED,    // 已完成
    CANCELLED     // 已取消
}

/**
 * 排产策略枚举
 */
enum class SchedulingStrategy {
    CAPACITY_FIRST,  // 产能优先
    TIME_FIRST,      // 时间优先
    ORDER_FIRST,     // 订单优先
    BALANCED         // 平衡策略
}

/**
 * 排产约束条件
 */
data class SchedulingConstraints(
    val workDaysPerMonth: Int = 22,        // 每月工作日数
    val shiftHours: Int = 8,               // 每班工作小时
    val bufferDays: Int = 2,               // 缓冲天数
    val respectDeadline: Boolean = true,   // 严格遵守交付期
    val considerCapacity: Boolean = true,  // 考虑机台产能限制
    val avoidOvertime: Boolean = false,    // 避免加班生产
    val balanceLoad: Boolean = true        // 平衡机台负载
)

/**
 * 机台信息
 */
data class Machine(
    val id: String,                    // 机台ID
    val name: String,                  // 机台名称
    val capacity: Int,                 // 日产能
    val efficiency: Double = 1.0,      // 效率系数
    val isAvailable: Boolean = true,   // 是否可用
    val maintenanceDate: LocalDate? = null // 维护日期
)

/**
 * 排产结果
 */
data class SchedulingResult(
    val orders: List<ProductionOrder>,     // 排产后的订单列表
    val machineSchedule: Map<String, List<ProductionOrder>>, // 机台排产计划
    val totalProductionDays: Int,          // 总生产天数
    val utilizationRate: Double,           // 机台利用率
    val onTimeDeliveryRate: Double,        // 按时交付率
    val conflicts: List<String> = emptyList() // 冲突信息
)

/**
 * 排产统计信息
 */
data class SchedulingStatistics(
    val totalOrders: Int,              // 总订单数
    val completedOrders: Int,          // 已完成订单数
    val pendingOrders: Int,            // 待排产订单数
    val urgentOrders: Int,             // 紧急订单数
    val totalQuantity: Int,            // 总数量
    val averageProductionDays: Double, // 平均生产天数
    val machineUtilization: Map<String, Double> // 各机台利用率
)

/**
 * 管规格信息
 */
data class PipeSpecification(
    val innerDiameter: Double,
    val outerDiameter: Double,
    val isLarge: Boolean = false,
    val hasCones: Boolean = false,     // 是否有锥形
    val coneType: String = ""          // 锥形类型：小锥/大锥
)

/**
 * 机台规则
 */
data class MachineRule(
    val machineId: String,             // 机台ID (1#, 2#, 3#, 4#, 5#, 6#, 7#)
    val moldId: String,                // 模具ID
    val pipeSpecs: List<PipeSpecification>,
    val description: String,           // 说明
    val changeoverTime: Int = 4,       // 换模具时间(小时)
    val pipeChangeTime: Int = 12,      // 换接口时间(小时)
    val interchangeableWith: List<String> = emptyList() // 可互换机台
)

/**
 * 发货优先级
 */
data class ShippingPriority(
    val orderId: String,
    val customerContractNumber: String,
    val deliveryDate: LocalDate,
    val priority: Int,                 // 1-5优先级
    val reason: String
)

/**
 * 机台分配结果
 */
data class MachineAssignment(
    val machineId: String,
    val moldId: String,
    val changeoverTime: Int,
    val pipeChangeTime: Int,
    val totalSetupTime: Int,
    val cost: Double
)
