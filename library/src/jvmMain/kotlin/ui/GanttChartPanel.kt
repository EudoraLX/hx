package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.SchedulingResult
import io.github.kotlin.fibonacci.model.ProductionOrder
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.*

/**
 * 每日任务数据类
 */
data class DailyTask(
    val date: LocalDate,
    val taskType: TaskType,
    val orderId: String? = null,
    val orderQuantity: Int = 0,
    val moldId: String? = null,
    val changeoverTime: Int = 0, // 换模时间（小时）
    val productionTime: Int = 0, // 生产时间（小时）
    val description: String = ""
)

/**
 * 任务类型枚举
 */
enum class TaskType {
    PRODUCTION,    // 生产任务
    MOLD_CHANGEOVER, // 换模任务
    PIPE_CHANGEOVER  // 换接口任务
}

/**
 * 甘特图面板
 * 显示排产结果的甘特图
 */
class GanttChartPanel : JPanel() {
    
    private var schedulingResult: SchedulingResult? = null
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now().plusDays(30)
    private val dayWidth = 80  // 增加每日框的宽度，显示更多信息
    private val rowHeight = 60  // 增加行高，显示更多任务
    private val headerHeight = 80  // 增加表头高度
    private val maxTasksPerDay = 3  // 一天最多3个任务
    private val machineNames = mutableListOf<String>()
    private val orderColors = mutableMapOf<String, Color>()
    private val changeoverColors = mutableMapOf<String, Color>()
    private val dailySchedule = mutableMapOf<String, MutableList<DailyTask>>()
    
    init {
        background = Color.WHITE
        isOpaque = true
        preferredSize = Dimension(800, 600)
        
        // 添加鼠标滚轮缩放
        addMouseWheelListener { e ->
            val scale = if (e.wheelRotation < 0) 1.1 else 0.9
            val newDayWidth = (dayWidth * scale).toInt().coerceIn(10, 100)
            // 这里可以添加缩放逻辑
        }
    }
    
    fun updateSchedulingResult(result: SchedulingResult) {
        this.schedulingResult = result
        
        // 计算日期范围
        val allDates: List<LocalDate> = result.orders.flatMap { order ->
            listOfNotNull(order.startDate, order.endDate)
        }
        
        if (allDates.isNotEmpty()) {
            startDate = allDates.minOrNull() ?: LocalDate.now()
            endDate = allDates.maxOrNull() ?: LocalDate.now().plusDays(30)
        }
        
        // 获取机台列表
        machineNames.clear()
        machineNames.addAll(result.machineSchedule.keys.sorted())
        
        // 生成每日任务计划
        generateDailySchedule(result)
        
        // 为每个订单分配颜色
        orderColors.clear()
        val colors = arrayOf(
            Color(255, 200, 200), Color(200, 255, 200), Color(200, 200, 255),
            Color(255, 255, 200), Color(255, 200, 255), Color(200, 255, 255),
            Color(255, 180, 180), Color(180, 255, 180), Color(180, 180, 255),
            Color(255, 255, 180), Color(255, 180, 255), Color(180, 255, 255)
        )
        
        result.orders.forEachIndexed { index, order ->
            orderColors[order.id] = colors[index % colors.size]
        }
        
        // 为换模任务分配颜色
        changeoverColors.clear()
        changeoverColors["MOLD_CHANGEOVER"] = Color(255, 165, 0) // 橙色
        changeoverColors["PIPE_CHANGEOVER"] = Color(255, 69, 0)  // 红色
        
        repaint()
    }
    
    /**
     * 生成每日任务计划
     * 任务可按天进行分割，例如换管时间从23点开始，则到次日3点结束
     * 甘特图简单显示每天工作任务（包含h）
     */
    private fun generateDailySchedule(result: SchedulingResult) {
        dailySchedule.clear()
        
        // 为每个机台初始化任务列表
        machineNames.forEach { machineId ->
            dailySchedule[machineId] = mutableListOf()
        }
        
        // 处理每个机台的订单
        result.machineSchedule.forEach { (machineId, orders) ->
            var currentMold: String? = null
            var currentPipeSpec: String? = null
            
            orders.forEach { order ->
                val startDate = order.startDate ?: return@forEach
                val endDate = order.endDate ?: return@forEach
                
                // 获取订单的模具和管规格信息
                val moldId = getMoldForOrder(order, result.machineSchedule)
                val pipeSpec = "${order.innerDiameter}/${order.outerDiameter}"
                
                // 检查是否需要换模（12小时）
                if (currentMold != null && currentMold != moldId) {
                    val changeoverDate = startDate
                    dailySchedule[machineId]?.add(
                        DailyTask(
                            date = changeoverDate,
                            taskType = TaskType.MOLD_CHANGEOVER,
                            moldId = moldId,
                            changeoverTime = 12, // 换模12小时
                            description = "换模: $currentMold → $moldId (12h)"
                        )
                    )
                }
                
                // 检查是否需要换接口（4小时）
                if (currentPipeSpec != null && currentPipeSpec != pipeSpec) {
                    val changeoverDate = if (currentMold != null && currentMold != moldId) {
                        startDate.plusDays(1) // 如果同时换模和换接口，换接口在第二天
                    } else {
                        startDate
                    }
                    dailySchedule[machineId]?.add(
                        DailyTask(
                            date = changeoverDate,
                            taskType = TaskType.PIPE_CHANGEOVER,
                            changeoverTime = 4, // 换接口4小时
                            description = "换接口: $currentPipeSpec → $pipeSpec (4h)"
                        )
                    )
                }
                
                // 添加生产任务，按天分割，一天最多3个任务
                var currentDate = startDate
                while (!currentDate.isAfter(endDate)) {
                    // 检查当天是否已有3个任务
                    val existingTasks = dailySchedule[machineId]?.count { it.date == currentDate } ?: 0
                    if (existingTasks >= maxTasksPerDay) {
                        currentDate = currentDate.plusDays(1)
                        continue
                    }
                    
                    val productionHours = if (currentDate == startDate && (currentMold != null && currentMold != moldId || currentPipeSpec != null && currentPipeSpec != pipeSpec)) {
                        // 如果当天有换模或换接口，生产时间减少
                        val changeoverHours = if (currentMold != null && currentMold != moldId) 12 else 0
                        val pipeChangeHours = if (currentPipeSpec != null && currentPipeSpec != pipeSpec) 4 else 0
                        maxOf(0, 24 - changeoverHours - pipeChangeHours)
                    } else {
                        24 // 全天生产（每日限制工时为24h）
                    }
                    
                    // 将生产任务分割为最多3个任务
                    val tasksPerDay = minOf(maxTasksPerDay - existingTasks, maxOf(1, (productionHours / 8).toInt())) // 每8小时一个任务
                    val hoursPerTask = productionHours / tasksPerDay
                    
                    repeat(tasksPerDay) { taskIndex ->
                        dailySchedule[machineId]?.add(
                            DailyTask(
                                date = currentDate,
                                taskType = TaskType.PRODUCTION,
                                orderId = order.id,
                                orderQuantity = order.quantity / tasksPerDay,
                                moldId = moldId,
                                productionTime = hoursPerTask.toInt(),
                                description = "生产订单${order.id}: ${order.quantity / tasksPerDay}支 (${hoursPerTask.toInt()}h)"
                            )
                        )
                    }
                    
                    currentDate = currentDate.plusDays(1)
                }
                
                // 更新当前模具和管规格
                currentMold = moldId
                currentPipeSpec = pipeSpec
            }
        }
    }
    
    /**
     * 获取订单对应的模具
     */
    private fun getMoldForOrder(order: ProductionOrder, machineSchedule: Map<String, List<ProductionOrder>>): String {
        // 这里可以根据实际需求实现模具获取逻辑
        // 暂时返回一个默认值
        return "模具${order.machine}"
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        drawGanttChart(g2d)
    }
    
    private fun drawGanttChart(g2d: Graphics2D) {
        val width = width
        val height = height
        
        if (schedulingResult == null || machineNames.isEmpty()) {
            // 绘制空状态
            g2d.color = Color.GRAY
            g2d.font = Font("微软雅黑", Font.BOLD, 16)
            val message = "没有排产数据"
            val fm = g2d.fontMetrics
            val x = (width - fm.stringWidth(message)) / 2
            val y = height / 2
            g2d.drawString(message, x, y)
            return
        }
        
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val chartWidth = totalDays * dayWidth
        val chartHeight = (machineNames.size + 1) * rowHeight + headerHeight
        
        // 绘制背景
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        
        // 绘制标题
        drawTitle(g2d, width)
        
        // 绘制日期轴
        drawDateAxis(g2d, totalDays)
        
        // 绘制机台轴
        drawMachineAxis(g2d, machineNames.size)
        
        // 绘制甘特条
        drawGanttBars(g2d, totalDays)
        
        // 绘制图例
        drawLegend(g2d, width, height)
    }
    
    private fun drawTitle(g2d: Graphics2D, width: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 18)
        val title = "排产甘特图 - 每日任务安排"
        val fm = g2d.fontMetrics
        val x = (width - fm.stringWidth(title)) / 2
        g2d.drawString(title, x, 25)
        
        // 添加说明文字
        g2d.font = Font("微软雅黑", Font.PLAIN, 12)
        val explanation = "说明：每行代表一个机台，每列代表一天，彩色方块表示具体任务"
        val fm2 = g2d.fontMetrics
        val x2 = (width - fm2.stringWidth(explanation)) / 2
        g2d.drawString(explanation, x2, 45)
    }
    
    private fun drawDateAxis(g2d: Graphics2D, totalDays: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 10)
        
        val y = headerHeight - 5
        
        // 绘制日期标签 - 每天显示
        for (i in 0 until totalDays) {
            val date = startDate.plusDays(i.toLong())
            val x = 100 + i * dayWidth + dayWidth / 2
            
            val dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd"))
            val fm = g2d.fontMetrics
            val textX = x - fm.stringWidth(dateStr) / 2
            g2d.drawString(dateStr, textX, y)
            
            // 绘制日期分隔线
            g2d.color = Color.LIGHT_GRAY
            g2d.drawLine(100 + i * dayWidth, headerHeight, 100 + i * dayWidth, headerHeight + machineNames.size * rowHeight)
        }
        
        // 绘制日期轴线
        g2d.color = Color.BLACK
        g2d.drawLine(100, headerHeight, 100 + totalDays * dayWidth, headerHeight)
    }
    
    private fun drawMachineAxis(g2d: Graphics2D, machineCount: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        
        for (i in machineNames.indices) {
            val y = headerHeight + 20 + i * rowHeight
            val machineName = machineNames[i]
            g2d.drawString("机台$machineName", 10, y)
        }
        
        // 绘制机台轴线
        g2d.drawLine(90, headerHeight, 90, headerHeight + machineCount * rowHeight)
    }
    
    private fun drawGanttBars(g2d: Graphics2D, totalDays: Int) {
        dailySchedule.forEach { (machineId, tasks) ->
            val machineIndex = machineNames.indexOf(machineId)
            if (machineIndex >= 0) {
                val y = headerHeight + 5 + machineIndex * rowHeight
                
                // 按日期分组任务
                val tasksByDate = tasks.groupBy { it.date }
                
                // 为每一天绘制任务
                for (dayIndex in 0 until totalDays) {
                    val currentDate = startDate.plusDays(dayIndex.toLong())
                    val dayTasks = tasksByDate[currentDate] ?: emptyList()
                    val x = 100 + dayIndex * dayWidth
                    
                    if (dayTasks.isEmpty()) {
                        // 没有任务的日期显示为空白
                        g2d.color = Color.LIGHT_GRAY
                        g2d.fillRect(x, y, dayWidth - 2, rowHeight - 10)
                        g2d.color = Color.BLACK
                        g2d.drawRect(x, y, dayWidth - 2, rowHeight - 10)
                        
                        // 显示"无任务"
                        g2d.color = Color.GRAY
                        g2d.font = Font("微软雅黑", Font.PLAIN, 8)
                        val fm = g2d.fontMetrics
                        val textX = x + (dayWidth - fm.stringWidth("无任务")) / 2
                        val textY = y + (rowHeight - 10) / 2 + fm.height / 4
                        g2d.drawString("无任务", textX, textY)
                    } else {
                        // 有任务的日期，限制最多3个任务
                        var currentY = y
                        val limitedTasks = dayTasks.take(maxTasksPerDay) // 限制最多3个任务
                        val taskHeight = (rowHeight - 10) / limitedTasks.size
                        
                        limitedTasks.forEach { task ->
                            when (task.taskType) {
                                TaskType.PRODUCTION -> {
                                    // 绘制生产任务
                                    val color = orderColors[task.orderId ?: ""] ?: Color.LIGHT_GRAY
                                    g2d.color = color
                                    g2d.fillRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制边框
                                    g2d.color = Color.BLACK
                                    g2d.drawRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制订单信息
                                    g2d.color = Color.BLACK
                                    g2d.font = Font("微软雅黑", Font.BOLD, 8)
                                    val orderText = "订单${task.orderId}"
                                    val fm = g2d.fontMetrics
                                    val textX = x + (dayWidth - fm.stringWidth(orderText)) / 2
                                    val textY = currentY + taskHeight / 2 + fm.height / 4
                                    g2d.drawString(orderText, textX, textY)
                                    
                                    // 绘制数量和时间
                                    g2d.font = Font("微软雅黑", Font.PLAIN, 7)
                                    val detailText = "${task.orderQuantity}支 ${task.productionTime}h"
                                    val fm2 = g2d.fontMetrics
                                    val detailX = x + (dayWidth - fm2.stringWidth(detailText)) / 2
                                    val detailY = currentY + taskHeight / 2 + fm2.height + 2
                                    g2d.drawString(detailText, detailX, detailY)
                                    
                                    currentY += taskHeight
                                }
                                
                                TaskType.MOLD_CHANGEOVER -> {
                                    // 绘制换模任务
                                    val color = changeoverColors["MOLD_CHANGEOVER"] ?: Color.ORANGE
                                    g2d.color = color
                                    g2d.fillRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制边框
                                    g2d.color = Color.BLACK
                                    g2d.drawRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制换模信息
                                    g2d.color = Color.BLACK
                                    g2d.font = Font("微软雅黑", Font.BOLD, 8)
                                    val changeoverText = "换模${task.changeoverTime}h"
                                    val fm = g2d.fontMetrics
                                    val textX = x + (dayWidth - fm.stringWidth(changeoverText)) / 2
                                    val textY = currentY + taskHeight / 2 + fm.height / 4
                                    g2d.drawString(changeoverText, textX, textY)
                                    
                                    // 绘制模具信息
                                    g2d.font = Font("微软雅黑", Font.PLAIN, 7)
                                    val moldText = task.moldId ?: ""
                                    val fm2 = g2d.fontMetrics
                                    val moldX = x + (dayWidth - fm2.stringWidth(moldText)) / 2
                                    val moldY = currentY + taskHeight / 2 + fm2.height + 2
                                    g2d.drawString(moldText, moldX, moldY)
                                    
                                    currentY += taskHeight
                                }
                                
                                TaskType.PIPE_CHANGEOVER -> {
                                    // 绘制换接口任务
                                    val color = changeoverColors["PIPE_CHANGEOVER"] ?: Color.RED
                                    g2d.color = color
                                    g2d.fillRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制边框
                                    g2d.color = Color.BLACK
                                    g2d.drawRect(x, currentY, dayWidth - 2, taskHeight)
                                    
                                    // 绘制换接口信息
                                    g2d.color = Color.WHITE
                                    g2d.font = Font("微软雅黑", Font.BOLD, 8)
                                    val changeoverText = "换接口${task.changeoverTime}h"
                                    val fm = g2d.fontMetrics
                                    val textX = x + (dayWidth - fm.stringWidth(changeoverText)) / 2
                                    val textY = currentY + taskHeight / 2 + fm.height / 4
                                    g2d.drawString(changeoverText, textX, textY)
                                    
                                    // 绘制管规格信息
                                    g2d.font = Font("微软雅黑", Font.PLAIN, 7)
                                    val pipeText = "管规格"
                                    val fm2 = g2d.fontMetrics
                                    val pipeX = x + (dayWidth - fm2.stringWidth(pipeText)) / 2
                                    val pipeY = currentY + taskHeight / 2 + fm2.height + 2
                                    g2d.drawString(pipeText, pipeX, pipeY)
                                    
                                    currentY += taskHeight
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun drawLegend(g2d: Graphics2D, width: Int, height: Int) {
        if (schedulingResult == null) return
        
        val legendX = width - 200
        val legendY = 50
        val legendWidth = 180
        val legendHeight = 20 + schedulingResult!!.orders.size * 20 + 60 // 增加换模任务的空间
        
        // 绘制图例背景
        g2d.color = Color.LIGHT_GRAY
        g2d.fillRect(legendX, legendY, legendWidth, legendHeight)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX, legendY, legendWidth, legendHeight)
        
        // 绘制图例标题
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        g2d.drawString("任务图例", legendX + 10, legendY + 15)
        
        var currentY = legendY + 30
        
        // 绘制换模任务图例
        g2d.font = Font("微软雅黑", Font.BOLD, 10)
        g2d.color = Color.BLACK
        g2d.drawString("换模任务 (12h):", legendX + 10, currentY)
        currentY += 15
        
        g2d.color = changeoverColors["MOLD_CHANGEOVER"] ?: Color.ORANGE
        g2d.fillRect(legendX + 10, currentY - 10, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX + 10, currentY - 10, 15, 15)
        g2d.drawString("换模", legendX + 30, currentY)
        currentY += 20
        
        // 绘制换接口任务图例
        g2d.font = Font("微软雅黑", Font.BOLD, 10)
        g2d.color = Color.BLACK
        g2d.drawString("换接口任务 (4h):", legendX + 10, currentY)
        currentY += 15
        
        g2d.color = changeoverColors["PIPE_CHANGEOVER"] ?: Color.RED
        g2d.fillRect(legendX + 10, currentY - 10, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX + 10, currentY - 10, 15, 15)
        g2d.drawString("换接口", legendX + 30, currentY)
        currentY += 20
        
        // 绘制订单颜色
        g2d.font = Font("微软雅黑", Font.PLAIN, 10)
        g2d.color = Color.BLACK
        g2d.drawString("生产任务:", legendX + 10, currentY)
        currentY += 15
        
        schedulingResult!!.orders.forEachIndexed { index, order ->
            val y = currentY + index * 20
            val color = orderColors[order.id] ?: Color.LIGHT_GRAY
            
            // 绘制颜色块
            g2d.color = color
            g2d.fillRect(legendX + 10, y - 10, 15, 15)
            g2d.color = Color.BLACK
            g2d.drawRect(legendX + 10, y - 10, 15, 15)
            
            // 绘制订单信息
            val orderText = "${order.id}: ${order.companyModel} (${order.quantity}支)"
            g2d.drawString(orderText, legendX + 30, y)
        }
    }
    
    override fun getPreferredSize(): Dimension {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val width = 100 + totalDays * dayWidth + 200 // 额外空间给图例
        val height = headerHeight + machineNames.size * rowHeight + 50
        return Dimension(width, height)
    }
}
