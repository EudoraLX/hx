package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import io.github.kotlin.fibonacci.excel.*
import io.github.kotlin.fibonacci.core.*
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.awt.*
import java.io.File

/**
 * 文件管理面板
 */
class FileManagementPanel(
    private val uiManager: UIManager,
    private val previewPanel: JPanel
) {
    
    fun createPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createTitledBorder("文件管理")
        
        // 文件列表区域
        val fileListLabel = JLabel("已导入的表格:")
        fileListLabel.font = Font("微软雅黑", Font.BOLD, 12)
        panel.add(fileListLabel)
        panel.add(Box.createVerticalStrut(5))
        
        val fileList = JList<String>()
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        val fileScrollPane = JScrollPane(fileList)
        fileScrollPane.preferredSize = Dimension(320, 120)
        panel.add(fileScrollPane)
        panel.add(Box.createVerticalStrut(10))
        
        // 按钮区域
        val buttonPanel = JPanel()
        buttonPanel.layout = FlowLayout()
        
        val importButton = JButton("导入表格")
        val clearAllButton = JButton("清空全部")
        val mergeTwoTablesButton = JButton("合并两张表")
        val exportButton = JButton("导出合并结果")
        val downloadButton = JButton("下载当前预览")
        
        buttonPanel.add(importButton)
        buttonPanel.add(clearAllButton)
        buttonPanel.add(mergeTwoTablesButton)
        buttonPanel.add(exportButton)
        buttonPanel.add(downloadButton)
        
        panel.add(buttonPanel)
        panel.add(Box.createVerticalStrut(10))
        
        // 状态信息
        val statusLabel = JLabel("状态: 等待导入表格")
        statusLabel.font = Font("微软雅黑", Font.PLAIN, 11)
        panel.add(statusLabel)
        
        // 设置状态标签
        uiManager.setStatusLabel(statusLabel)
        
        // 绑定事件处理器
        bindEventHandlers(panel, fileList, importButton, clearAllButton, mergeTwoTablesButton, exportButton, downloadButton)
        
        return panel
    }
    
    private fun bindEventHandlers(
        panel: JPanel,
        fileList: JList<String>,
        importButton: JButton,
        clearAllButton: JButton,
        mergeTwoTablesButton: JButton,
        exportButton: JButton,
        downloadButton: JButton
    ) {
        // 导入按钮事件
        importButton.addActionListener {
            handleImportFiles(panel)
            updateFileList(fileList)
            uiManager.updatePreview()
        }
        
        // 清空全部按钮事件
        clearAllButton.addActionListener {
            uiManager.importedTables.clear()
            uiManager.mergedTable = null
            uiManager.smartMergeResult = null
            updateFileList(fileList)
            uiManager.updatePreview()
            uiManager.updateStatus("状态: 已清空所有表格")
        }
        
        // 合并按钮事件
        // 合并两张表按钮事件
        mergeTwoTablesButton.addActionListener {
            handleMergeTwoTables(panel)
            uiManager.updatePreview()
        }
        
        // 导出按钮事件
        exportButton.addActionListener {
            handleExport(panel)
        }
        
        // 下载按钮事件
        downloadButton.addActionListener {
            uiManager.handleDownloadCurrentPreview(panel)
        }
        
        // 文件列表选择事件
        fileList.addListSelectionListener {
            if (!fileList.valueIsAdjusting) {
                val selectedIndex = fileList.selectedIndex
                if (selectedIndex >= 0 && selectedIndex < uiManager.importedTables.size) {
                    val selectedTable = uiManager.importedTables[selectedIndex]
                    val tableName = selectedTable.fileName
                    uiManager.addDataSource(tableName, selectedTable)
                    uiManager.setCurrentDataSource(tableName)
                }
            }
        }
    }
    
    private fun handleImportFiles(panel: JPanel) {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Excel 文件 (*.xlsx, *.xls)", "xlsx", "xls")
        
        // 设置初始目录
        val lastDir: File? = uiManager.lastSelectedDirectory
        if (lastDir != null && lastDir.exists()) {
            fileChooser.currentDirectory = lastDir
        }
        
        val result = fileChooser.showOpenDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            uiManager.lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val selectedFiles = try {
                    fileChooser.selectedFiles
                } catch (e: Exception) {
                    val singleFile = fileChooser.selectedFile
                    if (singleFile != null) arrayOf(singleFile) else emptyArray()
                }
                
                if (selectedFiles.isEmpty()) {
                    val singleFile = fileChooser.selectedFile
                    if (singleFile != null) {
                        importSingleFile(singleFile)
                    }
                } else {
                    importMultipleFiles(selectedFiles)
                }
                
                uiManager.updateStatus("状态: 已导入 ${uiManager.importedTables.size} 个表格")
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "读取 Excel 文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    private fun importSingleFile(file: File) {
        val excelReader = ExcelReader()
        val excelData = excelReader.readExcel(file)
        val tableData = TableData(
            fileName = file.name,
            headers = excelData.headers,
            rows = excelData.rows,
            formulas = excelData.formulas
        )
        uiManager.importedTables.add(tableData)
    }
    
    private fun importMultipleFiles(files: Array<File>) {
        val excelReader = ExcelReader()
        files.forEach { file ->
            val excelData = excelReader.readExcel(file)
            val tableData = TableData(
                fileName = file.name,
                headers = excelData.headers,
                rows = excelData.rows,
                formulas = excelData.formulas
            )
            uiManager.importedTables.add(tableData)
        }
    }
    
    private fun handleSmartMerge(panel: JPanel) {
        if (uiManager.importedTables.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "请先导入至少一个表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        if (uiManager.importedTables.size == 1) {
            JOptionPane.showMessageDialog(panel, "只有一个表格，无需合并", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            val smartMerger = SmartTableMerger()
            val baseTable = uiManager.importedTables[0]
            val updateTable = uiManager.importedTables[1]
            
            uiManager.smartMergeResult = smartMerger.smartMerge(baseTable, updateTable)
            
            // 立即更新预览显示合并结果
            uiManager.updatePreview()
            
            val changesSummary = smartMerger.getChangesSummary(uiManager.smartMergeResult!!.changes)
            uiManager.updateStatus("状态: 智能合并完成，共 ${uiManager.smartMergeResult!!.updatedTable.rowCount} 行数据")
            
            JOptionPane.showMessageDialog(panel, 
                "$changesSummary\n\n请查看右侧预览区域的'原始数据'标签页查看合并结果", 
                "智能合并完成", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "智能合并失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun handleExport(panel: JPanel) {
        if (uiManager.smartMergeResult == null && uiManager.mergedTable == null) {
            JOptionPane.showMessageDialog(panel, "请先合并表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Excel 文件 (*.xlsx)", "xlsx")
        
        val lastDir: File? = uiManager.lastSelectedDirectory
        if (lastDir != null && lastDir.exists()) {
            fileChooser.currentDirectory = lastDir
        }
        
        val defaultFileName = if (uiManager.smartMergeResult != null) {
            val smartMerger = SmartTableMerger()
            smartMerger.generateFileName()
        } else {
            "合并结果.xlsx"
        }
        fileChooser.selectedFile = File(defaultFileName)
        
        val result = fileChooser.showSaveDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            uiManager.lastSelectedDirectory = fileChooser.currentDirectory
            
            try {
                val exporter = ExcelExporter()
                if (uiManager.smartMergeResult != null) {
                    val updatedTable = uiManager.smartMergeResult!!.updatedTable
                    val mergedTableData = MergedTableData(
                        sourceTables = listOf(uiManager.smartMergeResult!!.originalTable, updatedTable),
                        headers = updatedTable.headers,
                        rows = updatedTable.rows,
                        formulas = updatedTable.formulas
                    )
                    exporter.exportToExcel(mergedTableData, fileChooser.selectedFile)
                } else {
                    exporter.exportToExcel(uiManager.mergedTable!!, fileChooser.selectedFile)
                }
                JOptionPane.showMessageDialog(panel, "导出成功！\n文件保存为: ${fileChooser.selectedFile.name}", "成功", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "导出失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    private fun handleMergeTwoTables(panel: JPanel) {
        if (uiManager.importedTables.size < 2) {
            JOptionPane.showMessageDialog(panel, "请先导入至少两张表格", "提示", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        try {
            // 让用户选择两张表
            val tableNames = uiManager.importedTables.map { it.fileName }
            val outputTableName = JOptionPane.showInputDialog(
                panel, 
                "请选择输出表（包含公司型号、内径、外径等字段）:", 
                "选择输出表", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                tableNames.toTypedArray(), 
                tableNames[0]
            ) as? String ?: return
            
            val shippingPlanTableName = JOptionPane.showInputDialog(
                panel, 
                "请选择发货计划表（包含客户合同号、客户名称等字段）:", 
                "选择发货计划表", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                tableNames.toTypedArray(), 
                if (tableNames.size > 1) tableNames[1] else tableNames[0]
            ) as? String ?: return
            
            val outputTable = uiManager.importedTables.find { it.fileName == outputTableName }
            val shippingPlanTable = uiManager.importedTables.find { it.fileName == shippingPlanTableName }
            
            if (outputTable == null || shippingPlanTable == null) {
                JOptionPane.showMessageDialog(panel, "找不到指定的表格", "错误", JOptionPane.ERROR_MESSAGE)
                return
            }
            
            // 执行两张表合并
            val flowManager = SchedulingFlowManager()
            val mergedResult = flowManager.mergeTablesByCompanyModel(outputTable, shippingPlanTable)
            
            // 移除异常订单
            val filteredResult = removeAbnormalOrders(mergedResult)
            
            // 只保留发货计划表中的订单
            val shippingOrdersOnly = filterShippingOrdersOnly(filteredResult, shippingPlanTable)
            
            // 保存结果
            uiManager.smartMergeResult = MergeResult(
                originalTable = outputTable,
                updatedTable = shippingOrdersOnly,
                changes = emptyList()
            )
            
            // 添加到数据源
            uiManager.addDataSource("合并结果", shippingOrdersOnly)
            uiManager.setCurrentDataSource("合并结果")
            
            JOptionPane.showMessageDialog(panel, 
                "两张表合并完成！\n原始订单数: ${outputTable.rowCount}\n发货计划订单数: ${shippingPlanTable.rowCount}\n合并后订单数: ${shippingOrdersOnly.rowCount}\n已移除异常订单，只保留发货计划表中的订单", 
                "合并完成", JOptionPane.INFORMATION_MESSAGE)
                
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(panel, "合并失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    /**
     * 移除异常订单：没有endDate的、内径外径为0的
     */
    private fun removeAbnormalOrders(table: TableData): TableData {
        val filteredRows = mutableListOf<List<String>>()
        val filteredFormulas = mutableListOf<List<String?>>()
        
        table.rows.forEachIndexed { rowIndex, row ->
            val deliveryPeriod = getValueByHeader(row, table.headers, "交付期")
            val innerDiameter = getValueByHeader(row, table.headers, "内径")?.toDoubleOrNull() ?: 0.0
            val outerDiameter = getValueByHeader(row, table.headers, "外径")?.toDoubleOrNull() ?: 0.0
            
            // 检查是否为异常订单
            val hasValidDeliveryDate = deliveryPeriod != null && deliveryPeriod.isNotBlank()
            val hasValidDimensions = innerDiameter > 0 && outerDiameter > 0
            
            if (hasValidDeliveryDate && hasValidDimensions) {
                filteredRows.add(row)
                filteredFormulas.add(table.formulas.getOrNull(rowIndex) ?: List(table.headers.size) { null })
            }
        }
        
        return TableData(
            fileName = table.fileName,
            headers = table.headers,
            rows = filteredRows,
            formulas = filteredFormulas
        )
    }
    
    /**
     * 只保留发货计划表中的订单
     */
    private fun filterShippingOrdersOnly(table: TableData, shippingPlanTable: TableData): TableData {
        val filteredRows = mutableListOf<List<String>>()
        val filteredFormulas = mutableListOf<List<String?>>()
        
        // 创建发货计划表的公司型号集合
        val shippingCompanyModels = shippingPlanTable.rows.mapNotNull { row ->
            getValueByHeader(row, shippingPlanTable.headers, "公司型号")
        }.toSet()
        
        table.rows.forEachIndexed { rowIndex, row ->
            val companyModel = getValueByHeader(row, table.headers, "公司型号")
            
            // 只保留在发货计划表中的订单
            if (companyModel != null && companyModel in shippingCompanyModels) {
                filteredRows.add(row)
                filteredFormulas.add(table.formulas.getOrNull(rowIndex) ?: List(table.headers.size) { null })
            }
        }
        
        return TableData(
            fileName = "发货计划订单表",
            headers = table.headers,
            rows = filteredRows,
            formulas = filteredFormulas
        )
    }
    
    private fun getValueByHeader(row: List<String>, headers: List<String>, headerName: String): String? {
        val index = headers.indexOf(headerName)
        return if (index >= 0 && index < row.size) row[index] else null
    }
    
    private fun updateFileList(fileList: JList<String>) {
        val fileNames = uiManager.importedTables.map { "${it.fileName} (${it.rowCount}行)" }
        fileList.model = javax.swing.DefaultListModel<String>().apply {
            fileNames.forEach { addElement(it) }
        }
    }
    
}
