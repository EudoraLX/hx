package io.github.kotlin.fibonacci

import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.*
import java.io.File

fun main() {
    SwingUtilities.invokeLater {
        createAndShowGUI()
    }
}

private fun createAndShowGUI() {
    val frame = JFrame("Excel 表格合并器")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(1000, 700)
    frame.setLocationRelativeTo(null)
    
    // 直接添加 Excel 面板，不使用标签页
    frame.contentPane.add(createExcelPanel())
    frame.isVisible = true
}


private fun createExcelPanel(): JPanel {
    val panel = JPanel()
    panel.layout = BorderLayout()
    
    // 标题
    val titleLabel = JLabel("Excel 表格合并器", SwingConstants.CENTER)
    titleLabel.font = Font("微软雅黑", Font.BOLD, 18)
    panel.add(titleLabel, BorderLayout.NORTH)
    
    // 主内容区域
    val mainPanel = JPanel()
    mainPanel.layout = BorderLayout()
    
    // 右侧预览区域
    val previewPanel = createPreviewPanel()
    mainPanel.add(previewPanel, BorderLayout.CENTER)
    
    // 左侧控制面板（传递预览组件引用）
    val controlPanel = createControlPanel(previewPanel)
    mainPanel.add(controlPanel, BorderLayout.WEST)
    
    panel.add(mainPanel, BorderLayout.CENTER)
    
    return panel
}

private fun createControlPanel(previewPanel: JPanel): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = BorderFactory.createTitledBorder("操作控制")
    panel.preferredSize = Dimension(300, 0)
    
    // 文件列表区域
    val fileListLabel = JLabel("已导入的表格:")
    fileListLabel.font = Font("微软雅黑", Font.BOLD, 12)
    panel.add(fileListLabel)
    panel.add(Box.createVerticalStrut(5))
    
    val fileList = JList<String>()
    fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    val fileScrollPane = JScrollPane(fileList)
    fileScrollPane.preferredSize = Dimension(280, 150)
    panel.add(fileScrollPane)
    panel.add(Box.createVerticalStrut(10))
    
    // 按钮区域
    val buttonPanel = JPanel()
    buttonPanel.layout = FlowLayout()
    
    val importButton = JButton("导入表格")
    val removeButton = JButton("移除选中")
    val clearAllButton = JButton("清空全部")
    val mergeButton = JButton("智能合并")
    val exportButton = JButton("导出合并结果")
    
    buttonPanel.add(importButton)
    buttonPanel.add(removeButton)
    buttonPanel.add(clearAllButton)
    buttonPanel.add(mergeButton)
    buttonPanel.add(exportButton)
    
    panel.add(buttonPanel)
    panel.add(Box.createVerticalStrut(10))
    
    // 状态信息
    val statusLabel = JLabel("状态: 等待导入表格")
    statusLabel.font = Font("微软雅黑", Font.PLAIN, 11)
    panel.add(statusLabel)
    
    // 数据存储
    val importedTables = mutableListOf<TableData>()
    var mergedTable: MergedTableData? = null
    var smartMergeResult: MergeResult? = null
    
    // 记住上次选择的路径
    var lastSelectedDirectory: File? = null
    
    // 获取预览组件
    val previewInfoLabel = previewPanel.getComponent(0) as JLabel
    val previewScrollPane = previewPanel.getComponent(1) as JScrollPane
    val previewTable = previewScrollPane.viewport.view as JTable
    
    // 计算最优列宽的函数
    fun calculateOptimalColumnWidth(table: JTable, columnIndex: Int): Int {
        val column = table.columnModel.getColumn(columnIndex)
        val headerWidth = table.getTableHeader().getFontMetrics(table.getTableHeader().font)
            .stringWidth(column.headerValue.toString()) + 20
        
        var maxWidth = headerWidth
        for (row in 0 until table.rowCount) {
            val cellValue = table.getValueAt(row, columnIndex)?.toString() ?: ""
            val cellWidth = table.getFontMetrics(table.font).stringWidth(cellValue) + 20
            maxWidth = maxOf(maxWidth, cellWidth)
        }
        
        // 限制最大宽度，避免列过宽
        return minOf(maxWidth, 300)
    }
    
    // 更新文件列表显示
    fun updateFileList() {
        val fileNames = importedTables.map { "${it.fileName} (${it.rowCount}行)" }
        fileList.model = javax.swing.DefaultListModel<String>().apply {
            fileNames.forEach { addElement(it) }
        }
    }
    
    // 更新预览显示
    fun updatePreview() {
        if (smartMergeResult != null) {
            // 显示智能合并后的数据
            val updatedTable = smartMergeResult!!.updatedTable
            val model = object : javax.swing.table.DefaultTableModel() {
                override fun getColumnCount(): Int = updatedTable.columnCount
                override fun getRowCount(): Int = updatedTable.rowCount
                override fun getValueAt(row: Int, col: Int): Any = updatedTable.getValue(row, col)
                override fun getColumnName(col: Int): String = updatedTable.headers[col]
                override fun isCellEditable(row: Int, col: Int): Boolean = false
            }
            previewTable.model = model
            
            // 自适应列宽 - 根据内容自动调整列宽
            val tableColumnModel = previewTable.columnModel
            for (i in 0 until tableColumnModel.columnCount) {
                val column = tableColumnModel.getColumn(i)
                column.preferredWidth = calculateOptimalColumnWidth(previewTable, i)
                column.minWidth = 80         // 设置最小宽度
            }
            
            previewInfoLabel.text = "智能合并结果预览 (${updatedTable.rowCount} 行, ${updatedTable.columnCount} 列)"
        } else if (mergedTable != null) {
            // 显示普通合并后的数据
            val model = object : javax.swing.table.DefaultTableModel() {
                override fun getColumnCount(): Int = mergedTable!!.columnCount
                override fun getRowCount(): Int = mergedTable!!.rowCount
                override fun getValueAt(row: Int, col: Int): Any = mergedTable!!.getValue(row, col)
                override fun getColumnName(col: Int): String = mergedTable!!.headers[col]
                override fun isCellEditable(row: Int, col: Int): Boolean = false
            }
            previewTable.model = model
            
            // 自适应列宽 - 根据内容自动调整列宽
            val tableColumnModel = previewTable.columnModel
            for (i in 0 until tableColumnModel.columnCount) {
                val column = tableColumnModel.getColumn(i)
                column.preferredWidth = calculateOptimalColumnWidth(previewTable, i)
                column.minWidth = 80         // 设置最小宽度
            }
            
            previewInfoLabel.text = "合并结果预览 (${mergedTable!!.rowCount} 行, ${mergedTable!!.columnCount} 列)"
        } else if (importedTables.isNotEmpty()) {
            // 显示第一个表格的数据
            val firstTable = importedTables[0]
            val model = object : javax.swing.table.DefaultTableModel() {
                override fun getColumnCount(): Int = firstTable.columnCount
                override fun getRowCount(): Int = firstTable.rowCount
                override fun getValueAt(row: Int, col: Int): Any = firstTable.getValue(row, col)
                override fun getColumnName(col: Int): String = firstTable.headers[col]
                override fun isCellEditable(row: Int, col: Int): Boolean = false
            }
            previewTable.model = model
            
            // 自适应列宽 - 根据内容自动调整列宽
            val tableColumnModel = previewTable.columnModel
            for (i in 0 until tableColumnModel.columnCount) {
                val column = tableColumnModel.getColumn(i)
                column.preferredWidth = calculateOptimalColumnWidth(previewTable, i)
                column.minWidth = 80         // 设置最小宽度
            }
            
            previewInfoLabel.text = "预览: ${firstTable.fileName} (${firstTable.rowCount} 行, ${firstTable.columnCount} 列)"
        } else {
            // 清空预览
            previewTable.model = javax.swing.table.DefaultTableModel()
            previewInfoLabel.text = "请先导入并合并表格"
        }
    }
    
    // 导入按钮事件
    importButton.addActionListener {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Excel 文件 (*.xlsx, *.xls)", "xlsx", "xls")
        
        // 设置初始目录为上次选择的路径
        if (lastSelectedDirectory != null && lastSelectedDirectory!!.exists()) {
            fileChooser.currentDirectory = lastSelectedDirectory
        }
        
        val result = fileChooser.showOpenDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            // 保存当前选择的目录
            lastSelectedDirectory = fileChooser.currentDirectory
            
            // 尝试获取选中的文件
            val selectedFiles = try {
                fileChooser.selectedFiles
            } catch (e: Exception) {
                // 如果 selectedFiles 不可用，尝试使用 selectedFile
                val singleFile = fileChooser.selectedFile
                if (singleFile != null) arrayOf(singleFile) else emptyArray()
            }
            
            if (selectedFiles.isEmpty()) {
                // 如果仍然没有文件，尝试使用 selectedFile
                val singleFile = fileChooser.selectedFile
                if (singleFile != null) {
                    try {
                        val excelReader = ExcelReader()
                        val excelData = excelReader.readExcel(singleFile)
                        val tableData = TableData(
                            fileName = singleFile.name,
                            headers = excelData.headers,
                            rows = excelData.rows,
                            formulas = excelData.formulas
                        )
                        importedTables.add(tableData)
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(panel, "读取 Excel 文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
                        return@addActionListener
                    }
                } else {
                    return@addActionListener
                }
            } else {
                // 处理多个文件
                try {
                    val excelReader = ExcelReader()
                    selectedFiles.forEach { file ->
                        val excelData = excelReader.readExcel(file)
                        val tableData = TableData(
                            fileName = file.name,
                            headers = excelData.headers,
                            rows = excelData.rows,
                            formulas = excelData.formulas
                        )
                        importedTables.add(tableData)
                    }
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(panel, "读取 Excel 文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }
            }
            
            updateFileList()
            updatePreview()
            statusLabel.text = "状态: 已导入 ${importedTables.size} 个表格"
        }
    }
    
    // 移除按钮事件
    removeButton.addActionListener {
        val selectedIndex = fileList.selectedIndex
        if (selectedIndex >= 0 && selectedIndex < importedTables.size) {
            importedTables.removeAt(selectedIndex)
            updateFileList()
            updatePreview()
            statusLabel.text = "状态: 已移除表格，剩余 ${importedTables.size} 个"
        }
    }
    
    // 清空全部按钮事件
    clearAllButton.addActionListener {
        importedTables.clear()
        mergedTable = null
        smartMergeResult = null
        updateFileList()
        updatePreview()
        statusLabel.text = "状态: 已清空所有表格"
    }
    
    // 合并按钮事件
    mergeButton.addActionListener {
        if (importedTables.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请先导入至少一个表格", "提示", JOptionPane.WARNING_MESSAGE)
            return@addActionListener
        }
        
        if (importedTables.size == 1) {
            JOptionPane.showMessageDialog(panel, "只有一个表格，无需合并", "提示", JOptionPane.WARNING_MESSAGE)
            return@addActionListener
        }
        
        try {
            val smartMerger = SmartTableMerger()
            val baseTable = importedTables[0] // 使用第一个表格作为基础表格
            val updateTable = importedTables[1] // 使用第二个表格作为更新表格
            
            smartMergeResult = smartMerger.smartMerge(baseTable, updateTable)
            
            updatePreview()
            
            val changesSummary = smartMerger.getChangesSummary(smartMergeResult!!.changes)
            statusLabel.text = "状态: 智能合并完成，共 ${smartMergeResult!!.updatedTable.rowCount} 行数据"
            
            // 显示变更详情
            JOptionPane.showMessageDialog(panel, changesSummary, "智能合并完成", JOptionPane.INFORMATION_MESSAGE)
            
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "智能合并失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    // 导出按钮事件
    exportButton.addActionListener {
        if (smartMergeResult == null && mergedTable == null) {
            JOptionPane.showMessageDialog(panel, "请先合并表格", "提示", JOptionPane.WARNING_MESSAGE)
            return@addActionListener
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        // 设置初始目录为上次选择的路径
        if (lastSelectedDirectory != null && lastSelectedDirectory!!.exists()) {
            fileChooser.currentDirectory = lastSelectedDirectory
        }
        
        // 根据合并类型设置默认文件名
        val defaultFileName = if (smartMergeResult != null) {
            val smartMerger = SmartTableMerger()
            smartMerger.generateFileName()
        } else {
            "合并结果.xlsx"
        }
        fileChooser.selectedFile = File(defaultFileName)
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            // 保存当前选择的目录
            lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val exporter = ExcelExporter()
                if (smartMergeResult != null) {
                    // 导出智能合并结果 - 直接使用更新后的表格数据
                    val updatedTable = smartMergeResult!!.updatedTable
                    val mergedTableData = MergedTableData(
                        sourceTables = listOf(smartMergeResult!!.originalTable, updatedTable),
                        headers = updatedTable.headers,
                        rows = updatedTable.rows,
                        formulas = updatedTable.formulas
                    )
                    exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                } else {
                    // 导出普通合并结果
                    exporter.exportToExcel(mergedTable!!, fileChooser.selectedFile)
                }
                JOptionPane.showMessageDialog(panel, "导出成功！\n文件保存为: ${fileChooser.selectedFile.name}", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    return panel
}

private fun createPreviewPanel(): JPanel {
    val panel = JPanel()
    panel.layout = BorderLayout()
    panel.border = BorderFactory.createTitledBorder("数据预览")
    
    // 表格显示区域
    val table = JTable()
    val scrollPane = JScrollPane(table)
    
    // 设置表格的自动调整模式，允许水平滚动和自适应列宽
    table.autoResizeMode = JTable.AUTO_RESIZE_OFF
    
    // 预览信息标签
    val previewInfoLabel = JLabel("请先导入并合并表格")
    previewInfoLabel.font = Font("微软雅黑", Font.PLAIN, 12)
    previewInfoLabel.horizontalAlignment = SwingConstants.CENTER
    
    panel.add(previewInfoLabel, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)
    
    return panel
}
