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
    private val dayWidth = 120
    private val rowHeight = 60
    private val headerHeight = 80
    private val machineNames = mutableListOf<String>()
    private val orderColors = mutableMapOf<String, Color>()
    private val changeoverColors = mutableMapOf<String, Color>()
    private val dailySchedule = mutableMapOf<String, MutableList<DailyTask>>()
    
    init {
        background = Color.WHITE
        isOpaque = true
        preferredSize = Dimension(1200, 600)
        
        // 添加鼠标交互
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                handleMouseClick(e)
            }
            
            override fun mouseMoved(e: MouseEvent) {
                handleMouseMove(e)
            }
        })
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
     * 连续排产，直观显示换模换管时间
     */
    private fun generateDailySchedule(result: SchedulingResult) {
        dailySchedule.clear()
        
        // 为每个机台初始化任务列表
        machineNames.forEach { machineId ->
            dailySchedule[machineId] = mutableListOf()
        }
        
        // 处理每个机台的订单 - 简化显示，只显示生产任务
        result.machineSchedule.forEach { (machineId, orders) ->
            orders.forEach { order ->
                val startDate = order.startDate ?: return@forEach
                val endDate = order.endDate ?: return@forEach
                
                // 只添加生产任务，不显示换模换管
                dailySchedule[machineId]?.add(
                    DailyTask(
                        date = startDate,
                        taskType = TaskType.PRODUCTION,
                        orderId = order.id,
                        orderQuantity = order.quantity,
                        productionTime = 0,
                        description = "订单${order.id} (${order.quantity}支)"
                    )
                )
            }
        }
    }
    
    /**
     * 绘制甘特条 - 简化显示
     */
    private fun drawGanttBars(g2d: Graphics2D, totalDays: Int) {
        val startX = 150
        val startY = headerHeight + 50
        
        machineNames.forEachIndexed { machineIndex, machineId ->
            val y = startY + machineIndex * rowHeight
            
            // 绘制机台背景
            g2d.color = Color(240, 240, 240)
            g2d.fillRect(startX, y, totalDays * dayWidth, rowHeight - 5)
            
            // 绘制机台边框
            g2d.color = Color.GRAY
            g2d.drawRect(startX, y, totalDays * dayWidth, rowHeight - 5)
            
            // 绘制该机台的任务
            val tasks = dailySchedule[machineId] ?: emptyList()
            tasks.forEach { task ->
                val dayIndex = ChronoUnit.DAYS.between(startDate, task.date).toInt()
                if (dayIndex >= 0 && dayIndex < totalDays) {
                    val x = startX + dayIndex * dayWidth
                    
                    // 根据任务类型选择颜色
                    val color = when (task.taskType) {
                        TaskType.PRODUCTION -> orderColors[task.orderId] ?: Color.BLUE
                        TaskType.MOLD_CHANGEOVER -> Color(255, 165, 0) // 橙色 - 换模
                        TaskType.PIPE_CHANGEOVER -> Color(255, 69, 0)  // 红色 - 换管
                    }
                    
                    g2d.color = color
                    g2d.fillRect(x + 2, y + 2, dayWidth - 4, rowHeight - 9)
                    
                    // 绘制任务文本
                    g2d.color = Color.BLACK
                    g2d.font = Font("微软雅黑", Font.PLAIN, 10)
                    val text = task.description
                    val fm = g2d.fontMetrics
                    val textWidth = fm.stringWidth(text)
                    
                    if (textWidth <= dayWidth - 4) {
                        val textX = x + (dayWidth - textWidth) / 2
                        val textY = y + rowHeight / 2 + fm.height / 4
                        g2d.drawString(text, textX, textY)
                    } else {
                        // 文本太长，只显示订单号
                        val shortText = "订单${task.orderId}"
                        val shortWidth = fm.stringWidth(shortText)
                        val textX = x + (dayWidth - shortWidth) / 2
                        val textY = y + rowHeight / 2 + fm.height / 4
                        g2d.drawString(shortText, textX, textY)
                    }
                }
            }
        }
    }
    
    /**
     * 绘制图例 - 简化版本
     */
    private fun drawLegend(g2d: Graphics2D, width: Int, height: Int) {
        val legendY = height - 80
        val legendX = 20
        
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        g2d.drawString("图例:", legendX, legendY)
        
        g2d.font = Font("微软雅黑", Font.PLAIN, 10)
        
        // 生产任务
        g2d.color = Color.BLUE
        g2d.fillRect(legendX, legendY + 10, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawString("生产任务", legendX + 20, legendY + 22)
        
        // 换模任务
        g2d.color = Color(255, 165, 0) // 橙色
        g2d.fillRect(legendX + 100, legendY + 10, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawString("换模(12h)", legendX + 120, legendY + 22)
        
        // 换管任务
        g2d.color = Color(255, 69, 0) // 红色
        g2d.fillRect(legendX + 200, legendY + 10, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawString("换管(4h)", legendX + 220, legendY + 22)
        
        // 说明
        g2d.color = Color.GRAY
        g2d.drawString("说明：连续排产，换模换管时间单独显示", legendX, legendY + 40)
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
        val startX = 150
        val y = headerHeight + 30
        
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        
        for (i in 0 until totalDays) {
            val date = startDate.plusDays(i.toLong())
            val x = startX + i * dayWidth
            val dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd"))
            
            val fm = g2d.fontMetrics
            val textX = x + (dayWidth - fm.stringWidth(dateStr)) / 2
            g2d.drawString(dateStr, textX, y)
        }
    }
    
    private fun drawMachineAxis(g2d: Graphics2D, machineCount: Int) {
        val x = 20
        val startY = headerHeight + 50
        
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        
        machineNames.forEachIndexed { index, machineName ->
            val y = startY + index * rowHeight + rowHeight / 2
            g2d.drawString(machineName, x, y)
        }
    }
    
    private fun handleMouseClick(e: MouseEvent) {
        // 处理鼠标点击事件
    }
    
    private fun handleMouseMove(e: MouseEvent) {
        // 处理鼠标移动事件
    }
    
    private fun getMoldForOrder(order: ProductionOrder, machineSchedule: Map<String, List<ProductionOrder>>): String {
        // 根据订单找到对应的模具
        val machineId = order.machine
        val machineOrders = machineSchedule[machineId] ?: emptyList()
        val orderIndex = machineOrders.indexOfFirst { it.id == order.id }
        
        return if (orderIndex >= 0) {
            "模具${orderIndex + 1}"
        } else {
            "未知模具"
        }
    }
}