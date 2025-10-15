package io.github.kotlin.fibonacci

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.File
import java.io.FileInputStream

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
                        rowData.add(getCellValueAsString(cell))
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
        
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue.toString()
                    } else {
                        cell.numericCellValue.toString()
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue
                    } catch (e: Exception) {
                        try {
                            cell.numericCellValue.toString()
                        } catch (e2: Exception) {
                            "公式错误"
                        }
                    }
                }
                CellType.ERROR -> "错误"
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
