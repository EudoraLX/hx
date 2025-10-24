package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.SchedulingResult
import io.github.kotlin.fibonacci.model.ProductionOrder
import io.github.kotlin.fibonacci.model.OrderPriority
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.swing.*

/**
 * 增强版甘特图面板
 * 更直观的排产可视化
 */
class EnhancedGanttChartPanel : JPanel() {
    
    private var schedulingResult: SchedulingResult? = null
    private var startDate: LocalDate = LocalDate.now()
    private var endDate: LocalDate = LocalDate.now().plusDays(30)
    private val dayWidth = 120  // 增加每日框的宽度
    private val rowHeight = 80  // 增加行高
    private val headerHeight = 100  // 增加表头高度
    private val machineNames = mutableListOf<String>()
    private val orderColors = mutableMapOf<String, Color>()
    private val dailySchedule = mutableMapOf<String, MutableList<OrderTask>>()
    private var hoveredTask: OrderTask? = null
    private var hoveredPoint: Point? = null
    
    // 任务数据类
    data class OrderTask(
        val order: ProductionOrder,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val machineId: String,
        val isChangeover: Boolean = false,
        val changeoverType: String = "", // "MOLD" 或 "PIPE"
        val changeoverHours: Int = 0
    )
    
    init {
        background = Color.WHITE
        isOpaque = true
        preferredSize = Dimension(1200, 800)
        
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
        
        // 生成任务计划
        generateOrderTasks(result)
        
        // 为每个订单分配颜色（基于优先级）
        orderColors.clear()
        result.orders.forEach { order ->
            orderColors[order.id] = getColorByPriority(order.priority)
        }
        
        repaint()
    }
    
    /**
     * 根据优先级获取颜色
     */
    private fun getColorByPriority(priority: OrderPriority): Color {
        return when (priority) {
            OrderPriority.URGENT -> Color(255, 100, 100) // 红色 - 紧急
            OrderPriority.HIGH -> Color(255, 200, 100)   // 橙色 - 高
            OrderPriority.MEDIUM -> Color(100, 200, 255) // 蓝色 - 中
            OrderPriority.LOW -> Color(150, 255, 150)    // 绿色 - 低
        }
    }
    
    /**
     * 生成订单任务
     */
    private fun generateOrderTasks(result: SchedulingResult) {
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
                val moldId = getMoldForOrder(order)
                val pipeSpec = "${order.innerDiameter}/${order.outerDiameter}"
                
                // 检查是否需要换模
                if (currentMold != null && currentMold != moldId) {
                    dailySchedule[machineId]?.add(
                        OrderTask(
                            order = order,
                            startDate = startDate,
                            endDate = startDate,
                            machineId = machineId,
                            isChangeover = true,
                            changeoverType = "MOLD",
                            changeoverHours = 12
                        )
                    )
                }
                
                // 检查是否需要换管
                if (currentPipeSpec != null && currentPipeSpec != pipeSpec) {
                    val changeoverDate = if (currentMold != null && currentMold != moldId) {
                        startDate.plusDays(1)
                    } else {
                        startDate
                    }
                    dailySchedule[machineId]?.add(
                        OrderTask(
                            order = order,
                            startDate = changeoverDate,
                            endDate = changeoverDate,
                            machineId = machineId,
                            isChangeover = true,
                            changeoverType = "PIPE",
                            changeoverHours = 4
                        )
                    )
                }
                
                // 添加生产任务
                dailySchedule[machineId]?.add(
                    OrderTask(
                        order = order,
                        startDate = startDate,
                        endDate = endDate,
                        machineId = machineId,
                        isChangeover = false
                    )
                )
                
                // 更新当前模具和管规格
                currentMold = moldId
                currentPipeSpec = pipeSpec
            }
        }
    }
    
    /**
     * 获取订单对应的模具
     */
    private fun getMoldForOrder(order: ProductionOrder): String {
        // 这里可以根据实际需求实现模具获取逻辑
        return "模具${order.machine}"
    }
    
    /**
     * 处理鼠标点击
     */
    private fun handleMouseClick(e: MouseEvent) {
        val task = getTaskAtPoint(e.point)
        if (task != null) {
            showTaskDetails(task)
        }
    }
    
    /**
     * 处理鼠标移动
     */
    private fun handleMouseMove(e: MouseEvent) {
        val task = getTaskAtPoint(e.point)
        if (task != hoveredTask) {
            hoveredTask = task
            hoveredPoint = e.point
            repaint()
        }
    }
    
    /**
     * 获取指定点的任务
     */
    private fun getTaskAtPoint(point: Point): OrderTask? {
        if (schedulingResult == null) return null
        
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        
        dailySchedule.forEach { (machineId, tasks) ->
            val machineIndex = machineNames.indexOf(machineId)
            if (machineIndex >= 0) {
                val y = headerHeight + 10 + machineIndex * rowHeight
                
                tasks.forEach { task ->
                    val startDayIndex = ChronoUnit.DAYS.between(startDate, task.startDate).toInt()
                    val endDayIndex = ChronoUnit.DAYS.between(startDate, task.endDate).toInt()
                    
                    val startX = 100 + startDayIndex * dayWidth
                    val endX = 100 + (endDayIndex + 1) * dayWidth
                    val taskWidth = endX - startX
                    
                    if (point.x >= startX && point.x <= endX && 
                        point.y >= y && point.y <= y + rowHeight - 20) {
                        return task
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * 显示任务详情
     */
    private fun showTaskDetails(task: OrderTask) {
        val message = if (task.isChangeover) {
            "换${if (task.changeoverType == "MOLD") "模" else "管"}任务\n" +
            "机台: ${task.machineId}\n" +
            "时间: ${task.changeoverHours}小时\n" +
            "日期: ${task.startDate}"
        } else {
            "订单: ${task.order.id}\n" +
            "机台: ${task.machineId}\n" +
            "数量: ${task.order.quantity}支\n" +
            "开始: ${task.startDate}\n" +
            "结束: ${task.endDate}\n" +
            "优先级: ${task.order.priority}\n" +
            "状态: ${task.order.schedulingStatus}"
        }
        
        JOptionPane.showMessageDialog(this, message, "任务详情", JOptionPane.INFORMATION_MESSAGE)
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        drawEnhancedGanttChart(g2d)
    }
    
    private fun drawEnhancedGanttChart(g2d: Graphics2D) {
        val width = width
        val height = height
        
        if (schedulingResult == null || machineNames.isEmpty()) {
            drawEmptyState(g2d, width, height)
            return
        }
        
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val chartWidth = totalDays * dayWidth
        val chartHeight = (machineNames.size + 1) * rowHeight + headerHeight
        
        // 绘制背景
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        
        // 绘制标题
        drawEnhancedTitle(g2d, width)
        
        // 绘制日期轴
        drawEnhancedDateAxis(g2d, totalDays)
        
        // 绘制机台轴
        drawEnhancedMachineAxis(g2d, machineNames.size)
        
        // 绘制甘特条（连续条形图）
        drawContinuousGanttBars(g2d, totalDays)
        
        // 绘制图例
        drawEnhancedLegend(g2d, width, height)
        
        // 绘制悬停提示
        if (hoveredTask != null && hoveredPoint != null) {
            drawHoverTooltip(g2d, hoveredTask!!, hoveredPoint!!)
        }
    }
    
    private fun drawEmptyState(g2d: Graphics2D, width: Int, height: Int) {
        g2d.color = Color.GRAY
        g2d.font = Font("微软雅黑", Font.BOLD, 16)
        val message = "没有排产数据"
        val fm = g2d.fontMetrics
        val x = (width - fm.stringWidth(message)) / 2
        val y = height / 2
        g2d.drawString(message, x, y)
    }
    
    private fun drawEnhancedTitle(g2d: Graphics2D, width: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 20)
        val title = "排产甘特图 - 连续任务视图"
        val fm = g2d.fontMetrics
        val x = (width - fm.stringWidth(title)) / 2
        g2d.drawString(title, x, 30)
        
        // 添加说明文字
        g2d.font = Font("微软雅黑", Font.PLAIN, 12)
        val explanation = "连续条形表示跨天订单，颜色表示优先级，悬停查看详情"
        val fm2 = g2d.fontMetrics
        val x2 = (width - fm2.stringWidth(explanation)) / 2
        g2d.drawString(explanation, x2, 50)
    }
    
    private fun drawEnhancedDateAxis(g2d: Graphics2D, totalDays: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        
        val y = headerHeight - 10
        
        // 绘制日期标签
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
    
    private fun drawEnhancedMachineAxis(g2d: Graphics2D, machineCount: Int) {
        g2d.color = Color.BLACK
        g2d.font = Font("微软雅黑", Font.BOLD, 14)
        
        for (i in machineNames.indices) {
            val y = headerHeight + 20 + i * rowHeight
            val machineName = machineNames[i]
            g2d.drawString("机台$machineName", 10, y)
        }
        
        // 绘制机台轴线
        g2d.drawLine(90, headerHeight, 90, headerHeight + machineCount * rowHeight)
    }
    
    private fun drawContinuousGanttBars(g2d: Graphics2D, totalDays: Int) {
        dailySchedule.forEach { (machineId, tasks) ->
            val machineIndex = machineNames.indexOf(machineId)
            if (machineIndex >= 0) {
                val y = headerHeight + 10 + machineIndex * rowHeight
                
                // 按开始日期排序任务
                val sortedTasks = tasks.sortedBy { it.startDate }
                
                sortedTasks.forEach { task ->
                    val startDayIndex = ChronoUnit.DAYS.between(startDate, task.startDate).toInt()
                    val endDayIndex = ChronoUnit.DAYS.between(startDate, task.endDate).toInt()
                    
                    val startX = 100 + startDayIndex * dayWidth
                    val endX = 100 + (endDayIndex + 1) * dayWidth
                    val taskWidth = endX - startX
                    
                    if (task.isChangeover) {
                        // 绘制换模/换管任务
                        val color = if (task.changeoverType == "MOLD") {
                            Color(255, 165, 0) // 橙色 - 换模
                        } else {
                            Color(255, 69, 0)  // 红色 - 换管
                        }
                        
                        g2d.color = color
                        g2d.fillRect(startX, y, taskWidth, rowHeight - 20)
                        
                        // 绘制边框
                        g2d.color = Color.BLACK
                        g2d.drawRect(startX, y, taskWidth, rowHeight - 20)
                        
                        // 绘制换模/换管信息
                        g2d.color = Color.WHITE
                        g2d.font = Font("微软雅黑", Font.BOLD, 10)
                        val changeoverText = "换${if (task.changeoverType == "MOLD") "模" else "管"} ${task.changeoverHours}h"
                        val fm = g2d.fontMetrics
                        val textX = startX + (taskWidth - fm.stringWidth(changeoverText)) / 2
                        val textY = y + (rowHeight - 20) / 2 + fm.height / 4
                        g2d.drawString(changeoverText, textX, textY)
                        
                    } else {
                        // 绘制生产任务（连续条形图）
                        val color = orderColors[task.order.id] ?: Color.LIGHT_GRAY
                        g2d.color = color
                        g2d.fillRect(startX, y, taskWidth, rowHeight - 20)
                        
                        // 绘制边框
                        g2d.color = Color.BLACK
                        g2d.drawRect(startX, y, taskWidth, rowHeight - 20)
                        
                        // 绘制订单信息
                        g2d.color = Color.BLACK
                        g2d.font = Font("微软雅黑", Font.BOLD, 12)
                        val orderText = "订单${task.order.id}"
                        val fm = g2d.fontMetrics
                        val textX = startX + (taskWidth - fm.stringWidth(orderText)) / 2
                        val textY = y + (rowHeight - 20) / 2 + fm.height / 4
                        g2d.drawString(orderText, textX, textY)
                        
                        // 绘制数量信息
                        g2d.font = Font("微软雅黑", Font.PLAIN, 10)
                        val quantityText = "${task.order.quantity}支"
                        val fm2 = g2d.fontMetrics
                        val quantityX = startX + (taskWidth - fm2.stringWidth(quantityText)) / 2
                        val quantityY = textY + fm2.height + 2
                        g2d.drawString(quantityText, quantityX, quantityY)
                        
                        // 绘制优先级指示器
                        val priorityColor = when (task.order.priority) {
                            OrderPriority.URGENT -> Color.RED
                            OrderPriority.HIGH -> Color.ORANGE
                            OrderPriority.MEDIUM -> Color.BLUE
                            OrderPriority.LOW -> Color.GREEN
                        }
                        g2d.color = priorityColor
                        g2d.fillRect(startX, y, 4, rowHeight - 20)
                    }
                }
            }
        }
    }
    
    private fun drawEnhancedLegend(g2d: Graphics2D, width: Int, height: Int) {
        if (schedulingResult == null) return
        
        val legendX = width - 250
        val legendY = 80
        val legendWidth = 230
        val legendHeight = 200 + schedulingResult!!.orders.size * 25
        
        // 绘制图例背景
        g2d.color = Color(240, 240, 240)
        g2d.fillRect(legendX, legendY, legendWidth, legendHeight)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX, legendY, legendWidth, legendHeight)
        
        // 绘制图例标题
        g2d.font = Font("微软雅黑", Font.BOLD, 14)
        g2d.drawString("图例说明", legendX + 10, legendY + 20)
        
        var currentY = legendY + 40
        
        // 绘制优先级颜色说明
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        g2d.color = Color.BLACK
        g2d.drawString("优先级颜色:", legendX + 10, currentY)
        currentY += 20
        
        val priorities = listOf(
            OrderPriority.URGENT to "紧急 (红色)",
            OrderPriority.HIGH to "高 (橙色)", 
            OrderPriority.MEDIUM to "中 (蓝色)",
            OrderPriority.LOW to "低 (绿色)"
        )
        
        priorities.forEach { (priority, description) ->
            val color = getColorByPriority(priority)
            g2d.color = color
            g2d.fillRect(legendX + 10, currentY - 12, 15, 15)
            g2d.color = Color.BLACK
            g2d.drawRect(legendX + 10, currentY - 12, 15, 15)
            g2d.drawString(description, legendX + 30, currentY)
            currentY += 20
        }
        
        currentY += 10
        
        // 绘制任务类型说明
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        g2d.color = Color.BLACK
        g2d.drawString("任务类型:", legendX + 10, currentY)
        currentY += 20
        
        // 换模任务
        g2d.color = Color(255, 165, 0)
        g2d.fillRect(legendX + 10, currentY - 12, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX + 10, currentY - 12, 15, 15)
        g2d.drawString("换模任务 (12h)", legendX + 30, currentY)
        currentY += 20
        
        // 换管任务
        g2d.color = Color(255, 69, 0)
        g2d.fillRect(legendX + 10, currentY - 12, 15, 15)
        g2d.color = Color.BLACK
        g2d.drawRect(legendX + 10, currentY - 12, 15, 15)
        g2d.drawString("换管任务 (4h)", legendX + 30, currentY)
        currentY += 30
        
        // 绘制订单列表
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        g2d.color = Color.BLACK
        g2d.drawString("订单列表:", legendX + 10, currentY)
        currentY += 20
        
        g2d.font = Font("微软雅黑", Font.PLAIN, 10)
        schedulingResult!!.orders.take(10).forEach { order -> // 只显示前10个订单
            val color = orderColors[order.id] ?: Color.LIGHT_GRAY
            g2d.color = color
            g2d.fillRect(legendX + 10, currentY - 10, 12, 12)
            g2d.color = Color.BLACK
            g2d.drawRect(legendX + 10, currentY - 10, 12, 12)
            
            val orderText = "${order.id}: ${order.quantity}支"
            g2d.drawString(orderText, legendX + 30, currentY)
            currentY += 15
        }
    }
    
    private fun drawHoverTooltip(g2d: Graphics2D, task: OrderTask, point: Point) {
        val tooltipText = if (task.isChangeover) {
            "换${if (task.changeoverType == "MOLD") "模" else "管"}: ${task.changeoverHours}h"
        } else {
            "订单${task.order.id}: ${task.order.quantity}支"
        }
        
        g2d.font = Font("微软雅黑", Font.BOLD, 12)
        val fm = g2d.fontMetrics
        val textWidth = fm.stringWidth(tooltipText)
        val textHeight = fm.height
        
        val tooltipX = point.x + 10
        val tooltipY = point.y - 10
        val tooltipWidth = textWidth + 10
        val tooltipHeight = textHeight + 6
        
        // 绘制工具提示背景
        g2d.color = Color(255, 255, 200)
        g2d.fillRect(tooltipX, tooltipY - tooltipHeight, tooltipWidth, tooltipHeight)
        g2d.color = Color.BLACK
        g2d.drawRect(tooltipX, tooltipY - tooltipHeight, tooltipWidth, tooltipHeight)
        
        // 绘制工具提示文字
        g2d.drawString(tooltipText, tooltipX + 5, tooltipY - 3)
    }
    
    override fun getPreferredSize(): Dimension {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val width = 100 + totalDays * dayWidth + 300 // 额外空间给图例
        val height = headerHeight + machineNames.size * rowHeight + 100
        return Dimension(width, height)
    }
}
