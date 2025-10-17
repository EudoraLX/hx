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
        val mergeButton = JButton("智能合并")
        val exportButton = JButton("导出合并结果")
        val downloadButton = JButton("下载当前预览")
        
        buttonPanel.add(importButton)
        buttonPanel.add(clearAllButton)
        buttonPanel.add(mergeButton)
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
        bindEventHandlers(panel, fileList, importButton, clearAllButton, mergeButton, exportButton, downloadButton)
        
        return panel
    }
    
    private fun bindEventHandlers(
        panel: JPanel,
        fileList: JList<String>,
        importButton: JButton,
        clearAllButton: JButton,
        mergeButton: JButton,
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
        mergeButton.addActionListener {
            handleSmartMerge(panel)
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
            
            val changesSummary = smartMerger.getChangesSummary(uiManager.smartMergeResult!!.changes)
            uiManager.updateStatus("状态: 智能合并完成，共 ${uiManager.smartMergeResult!!.updatedTable.rowCount} 行数据")
            
            JOptionPane.showMessageDialog(panel, changesSummary, "智能合并完成", JOptionPane.INFORMATION_MESSAGE)
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
    
    private fun updateFileList(fileList: JList<String>) {
        val fileNames = uiManager.importedTables.map { "${it.fileName} (${it.rowCount}行)" }
        fileList.model = javax.swing.DefaultListModel<String>().apply {
            fileNames.forEach { addElement(it) }
        }
    }
    
}
