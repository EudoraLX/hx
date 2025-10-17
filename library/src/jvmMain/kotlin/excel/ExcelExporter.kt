package io.github.kotlin.fibonacci.excel

import io.github.kotlin.fibonacci.model.*
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
            
            // 第一遍：设置所有空单元格为0，避免公式计算错误
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.createCell(colIndex)
                if (cellValue.isEmpty()) {
                    cell.setCellValue(0.0)
                    // 设置格式，让0值显示为空
                    val cellStyle = workbook.createCellStyle()
                    val dataFormat = workbook.createDataFormat()
                    cellStyle.dataFormat = dataFormat.getFormat("0;-0;;@") // 0值不显示
                    cell.cellStyle = cellStyle
                }
            }
            
            // 第二遍：设置实际值和公式
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.getCell(colIndex)
                val formula = tableData.getFormula(rowIndex, colIndex)
                
                // 调试输出前几行
                if (rowIndex < 3) {
                }
                
                if (formula != null) {
                    cell.setCellFormula(formula)
                } else {
                    // 设置普通值，保持与预览一致的格式化
                    if (cellValue.isNotEmpty()) {
                        // 检查是否为纯数字（包括小数）
                        val isNumeric = cellValue.matches(Regex("-?\\d+(\\.\\d+)?"))
                        
                        if (isNumeric) {
                            try {
                                val numericValue = cellValue.toDouble()
                                
                                // 智能格式化：整数显示为整数，小数显示为小数，0值不显示
                                val bigDecimal = java.math.BigDecimal(numericValue)
                                val rounded = bigDecimal.setScale(2, java.math.RoundingMode.HALF_UP)
                                
                                // 检查四舍五入后是否为整数
                                if (rounded.scale() == 0 || rounded.stripTrailingZeros().scale() <= 0) {
                                    // 整数：5 → 5
                                    cell.setCellValue(rounded.toLong().toDouble())
                                    val cellStyle = workbook.createCellStyle()
                                    val dataFormat = workbook.createDataFormat()
                                    cellStyle.dataFormat = dataFormat.getFormat("0;-0;;@") // 0值不显示
                                    cell.cellStyle = cellStyle
                                } else {
                                    // 小数：先格式化为字符串，再设置
                                    val formattedValue = String.format("%.2f", rounded.toDouble()).trimEnd('0').trimEnd('.')
                                    cell.setCellValue(formattedValue)
                                    
                                    // 应用格式，0值不显示
                                    val cellStyle = workbook.createCellStyle()
                                    val dataFormat = workbook.createDataFormat()
                                    cellStyle.dataFormat = dataFormat.getFormat("@") // 文本格式
                                    cell.cellStyle = cellStyle
                                }
                            } catch (e: Exception) {
                                // 如果数字转换失败，设置为字符串
                                cell.setCellValue(cellValue)
                            }
                        } else {
                            // 非数字，直接设置为字符串
                            cell.setCellValue(cellValue)
                        }
                    } else {
                        // 空值设置为0，避免公式计算时出现#VALUE!错误
                        cell.setCellValue(0.0)
                    }
                }
            }
        }
        
        // 强制重新计算所有公式
        workbook.creationHelper.createFormulaEvaluator().evaluateAll()
        
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
            
            // 第一遍：设置所有空单元格为0，避免公式计算错误
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.createCell(colIndex)
                if (cellValue.isEmpty()) {
                    cell.setCellValue(0.0)
                    // 设置格式，让0值显示为空
                    val cellStyle = workbook.createCellStyle()
                    val dataFormat = workbook.createDataFormat()
                    cellStyle.dataFormat = dataFormat.getFormat("0;-0;;@") // 0值不显示
                    cell.cellStyle = cellStyle
                }
            }
            
            // 第二遍：设置实际值和公式
            rowData.forEachIndexed { colIndex, cellValue ->
                val cell = dataRow.getCell(colIndex)
                val formula = tableData.getFormula(rowIndex, colIndex)
                
                // 调试输出前几行
                if (rowIndex < 3) {
                }
                
                if (formula != null) {
                    cell.setCellFormula(formula)
                } else {
                    // 设置普通值，保持与预览一致的格式化
                    if (cellValue.isNotEmpty()) {
                        // 检查是否为纯数字（包括小数）
                        val isNumeric = cellValue.matches(Regex("-?\\d+(\\.\\d+)?"))
                        
                        if (isNumeric) {
                            try {
                                val numericValue = cellValue.toDouble()
                                
                                // 智能格式化：整数显示为整数，小数显示为小数，0值不显示
                                val bigDecimal = java.math.BigDecimal(numericValue)
                                val rounded = bigDecimal.setScale(2, java.math.RoundingMode.HALF_UP)
                                
                                // 检查四舍五入后是否为整数
                                if (rounded.scale() == 0 || rounded.stripTrailingZeros().scale() <= 0) {
                                    // 整数：5 → 5
                                    cell.setCellValue(rounded.toLong().toDouble())
                                    val cellStyle = workbook.createCellStyle()
                                    val dataFormat = workbook.createDataFormat()
                                    cellStyle.dataFormat = dataFormat.getFormat("0;-0;;@") // 0值不显示
                                    cell.cellStyle = cellStyle
                                } else {
                                    // 小数：先格式化为字符串，再设置
                                    val formattedValue = String.format("%.2f", rounded.toDouble()).trimEnd('0').trimEnd('.')
                                    cell.setCellValue(formattedValue)
                                    
                                    // 应用格式，0值不显示
                                    val cellStyle = workbook.createCellStyle()
                                    val dataFormat = workbook.createDataFormat()
                                    cellStyle.dataFormat = dataFormat.getFormat("@") // 文本格式
                                    cell.cellStyle = cellStyle
                                }
                            } catch (e: Exception) {
                                // 如果数字转换失败，设置为字符串
                                cell.setCellValue(cellValue)
                            }
                        } else {
                            // 非数字，直接设置为字符串
                            cell.setCellValue(cellValue)
                        }
                    } else {
                        // 空值设置为0，避免公式计算时出现#VALUE!错误
                        cell.setCellValue(0.0)
                    }
                }
            }
        }
        
        // 强制重新计算所有公式
        workbook.creationHelper.createFormulaEvaluator().evaluateAll()
        
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
