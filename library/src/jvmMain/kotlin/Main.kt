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
    val frame = JFrame("Excel 数据预览器")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(800, 600)
    frame.setLocationRelativeTo(null)
    
    // 直接添加 Excel 面板，不使用标签页
    frame.contentPane.add(createExcelPanel())
    frame.isVisible = true
}


private fun createExcelPanel(): JPanel {
    val panel = JPanel()
    panel.layout = BorderLayout()
    
    // 标题
    val titleLabel = JLabel("Excel 文件导入和预览", SwingConstants.CENTER)
    titleLabel.font = Font("微软雅黑", Font.BOLD, 18)
    panel.add(titleLabel, BorderLayout.NORTH)
    
    // 按钮面板
    val buttonPanel = JPanel()
    buttonPanel.layout = FlowLayout()
    
    val importButton = JButton("选择 Excel 文件")
    val clearButton = JButton("清空预览")
    
    buttonPanel.add(importButton)
    buttonPanel.add(clearButton)
    
    // 文件信息标签
    val fileInfoLabel = JLabel("未选择文件")
    fileInfoLabel.font = Font("微软雅黑", Font.PLAIN, 12)
    
    // 表格显示区域
    val table = JTable()
    val scrollPane = JScrollPane(table)
    
    // 导入按钮事件
    importButton.addActionListener {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = FileNameExtensionFilter("Excel 文件 (*.xlsx, *.xls)", "xlsx", "xls")
        
        val result = fileChooser.showOpenDialog(panel)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            try {
                val excelReader = ExcelReader()
                val excelData = excelReader.readExcel(selectedFile)
                
                // 更新文件信息
                fileInfoLabel.text = "文件: ${selectedFile.name} | 行数: ${excelData.rows.size} | 列数: ${excelData.headers.size}"
                
                // 创建表格模型
                val model = object : javax.swing.table.DefaultTableModel() {
                    override fun getColumnCount(): Int = excelData.headers.size
                    override fun getRowCount(): Int = excelData.rows.size
                    override fun getValueAt(row: Int, col: Int): Any = excelData.rows[row][col]
                    override fun getColumnName(col: Int): String = excelData.headers[col]
                    override fun isCellEditable(row: Int, col: Int): Boolean = false
                }
                
                table.model = model
                
                // 调整列宽
                val tableColumnModel = table.columnModel
                for (i in 0 until tableColumnModel.columnCount) {
                    val column = tableColumnModel.getColumn(i)
                    column.preferredWidth = 120
                }
                
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(panel, "读取 Excel 文件失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    // 清空按钮事件
    clearButton.addActionListener {
        table.model = javax.swing.table.DefaultTableModel()
        fileInfoLabel.text = "未选择文件"
    }
    
    // 布局
    val topPanel = JPanel()
    topPanel.layout = BorderLayout()
    topPanel.add(buttonPanel, BorderLayout.NORTH)
    topPanel.add(fileInfoLabel, BorderLayout.SOUTH)
    
    panel.add(topPanel, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)
    
    return panel
}
