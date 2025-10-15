package io.github.kotlin.fibonacci

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class ExcelExporter {
    
    fun exportToExcel(tableData: MergedTableData, outputFile: File) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("合并数据")
        
        // 创建标题行
        val headerRow = sheet.createRow(0)
        tableData.headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            
            // 设置标题样式
            val cellStyle = workbook.createCellStyle()
            val font = workbook.createFont()
            font.bold = true
            cellStyle.setFont(font)
            cell.cellStyle = cellStyle
        }
        
        // 创建数据行
        tableData.rows.forEachIndexed { rowIndex, rowData ->
            val dataRow = sheet.createRow(rowIndex + 1)
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.createCell(colIndex)
                val formula = tableData.getFormula(rowIndex, colIndex)
                
                if (formula != null) {
                    // 设置公式
                    cell.setCellFormula(formula)
                } else {
                    // 设置普通值
                    cell.setCellValue(cellValue)
                }
            }
        }
        
        // 自动调整列宽
        for (i in 0 until tableData.columnCount) {
            sheet.autoSizeColumn(i)
        }
        
        // 写入文件
        FileOutputStream(outputFile).use { outputStream ->
            workbook.write(outputStream)
        }
        
        workbook.close()
    }
    
    fun exportToExcel(tableData: TableData, outputFile: File) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("表格数据")
        
        // 创建标题行
        val headerRow = sheet.createRow(0)
        tableData.headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            
            // 设置标题样式
            val cellStyle = workbook.createCellStyle()
            val font = workbook.createFont()
            font.bold = true
            cellStyle.setFont(font)
            cell.cellStyle = cellStyle
        }
        
        // 创建数据行
        tableData.rows.forEachIndexed { rowIndex, rowData ->
            val dataRow = sheet.createRow(rowIndex + 1)
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.createCell(colIndex)
                val formula = tableData.getFormula(rowIndex, colIndex)
                
                if (formula != null) {
                    // 设置公式
                    cell.setCellFormula(formula)
                } else {
                    // 设置普通值
                    cell.setCellValue(cellValue)
                }
            }
        }
        
        // 自动调整列宽
        for (i in 0 until tableData.columnCount) {
            sheet.autoSizeColumn(i)
        }
        
        // 写入文件
        FileOutputStream(outputFile).use { outputStream ->
            workbook.write(outputStream)
        }
        
        workbook.close()
    }
}
