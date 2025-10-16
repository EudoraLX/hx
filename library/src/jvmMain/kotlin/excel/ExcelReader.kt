package io.github.kotlin.fibonacci.excel

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date

data class ExcelData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val formulas: List<List<String?>> = emptyList()
)

class ExcelReader {
    
    fun readExcel(file: File): ExcelData {
        val workbook = when (file.extension.lowercase()) {
            "xlsx" -> XSSFWorkbook(FileInputStream(file))
            "xls" -> HSSFWorkbook(FileInputStream(file))
            else -> throw IllegalArgumentException("不支持的文件格式: ${file.extension}")
        }
        
        try {
            val sheet = workbook.getSheetAt(0)
            val headers = mutableListOf<String>()
            val rows = mutableListOf<List<String>>()
            val formulas = mutableListOf<List<String?>>()
            
            // 读取第一行作为标题
            val headerRow = sheet.getRow(0)
            if (headerRow != null) {
                for (cell in headerRow) {
                    headers.add(getCellValueAsString(cell))
                }
            }
            
            // 读取数据行
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex)
                if (row != null) {
                    val rowData = mutableListOf<String>()
                    val rowFormulas = mutableListOf<String?>()
                    
                    for (cellIndex in 0 until headers.size) {
                        val cell = row.getCell(cellIndex)
                        val cellValue = getCellValueAsString(cell)
                        // 如果单元格值包含错误，使用空字符串
                        val cleanValue = if (cellValue.contains("#VALUE!") || cellValue.contains("#ERROR!") || 
                                           cellValue.contains("VALUE") || cellValue.contains("ERROR")) {
                            ""
                        } else {
                            cellValue
                        }
                        rowData.add(cleanValue)
                        rowFormulas.add(getCellFormula(cell))
                    }
                    rows.add(rowData)
                    formulas.add(rowFormulas)
                }
            }
            
            return ExcelData(headers, rows, formulas)
        } finally {
            workbook.close()
        }
    }
    
    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""
        
        // 首先检查单元格是否包含错误
        if (cell.cellType == CellType.ERROR) {
            return ""
        }
        
        return try {
            when (cell.cellType) {
                CellType.STRING -> {
                    val stringValue = cell.stringCellValue
                    // 检查是否为错误文本
                    if (stringValue.startsWith("#") || stringValue.contains("VALUE") || stringValue.contains("ERROR")) {
                        ""
                    } else {
                        stringValue
                    }
                }
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // 格式化日期为 YYYY-MM-DD 格式
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
                        dateFormat.format(cell.dateCellValue)
                    } else {
                        // 格式化数字，确保显示2位小数
                        val value = cell.numericCellValue
                        // 检查是否为0值，如果是0则返回空字符串
                        if (value == 0.0) {
                            ""
                        } else if (value == value.toLong().toDouble()) {
                            // 如果是整数，显示为整数
                            value.toLong().toString()
                        } else {
                            // 如果是小数，显示2位小数
                            String.format("%.2f", value)
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        // 对于公式，先尝试获取计算结果
                        when (cell.cachedFormulaResultType) {
                            CellType.NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    // 格式化日期为 YYYY-MM-DD 格式
                                    val dateFormat = SimpleDateFormat("yyyy-MM-dd")
                                    dateFormat.format(cell.dateCellValue)
                                } else {
                                    val value = cell.numericCellValue
                                    // 检查是否为0值，如果是0则返回空字符串
                                    if (value == 0.0) {
                                        ""
                                    } else if (value == value.toLong().toDouble()) {
                                        value.toLong().toString()
                                    } else {
                                        String.format("%.2f", value)
                                    }
                                }
                            }
                            CellType.STRING -> {
                                val stringValue = cell.stringCellValue
                                // 检查是否为错误文本
                                if (stringValue.startsWith("#") || stringValue.contains("VALUE") || stringValue.contains("ERROR")) {
                                    ""
                                } else {
                                    stringValue
                                }
                            }
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            CellType.ERROR -> "" // 公式结果为错误时返回空字符串
                            else -> ""
                        }
                    } catch (e: Exception) {
                        // 公式计算失败时返回空字符串
                        ""
                    }
                }
                CellType.ERROR -> "" // 错误单元格返回空字符串
                CellType.BLANK -> ""
                else -> ""
            }
        } catch (e: Exception) {
            // 如果读取单元格值时出现任何异常，返回空字符串
            println("Error reading cell value: ${e.message}")
            ""
        }
    }
    
    private fun getCellFormula(cell: Cell?): String? {
        if (cell == null) return null
        
        return if (cell.cellType == CellType.FORMULA) {
            try {
                cell.cellFormula
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

