package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MergeResult(
    val originalTable: TableData,
    val updatedTable: TableData,
    val changes: List<CellChange>
)

data class CellChange(
    val row: Int,
    val column: Int,
    val originalValue: String,
    val newValue: String,
    val sourceTable: String
)

class SmartTableMerger {
    
    fun smartMerge(baseTable: TableData, updateTable: TableData): MergeResult {
        val changes = mutableListOf<CellChange>()
        val updatedRows = mutableListOf<List<String>>()
        val updatedFormulas = mutableListOf<List<String?>>()
        
        // 创建行索引映射（基于第一列或关键列）
        val baseRowMap = createRowMap(baseTable)
        val updateRowMap = createRowMap(updateTable)
        
        // 处理基础表格的每一行
        baseTable.rows.forEachIndexed { rowIndex, baseRow ->
            val updatedRow = baseRow.toMutableList()
            // 初始化公式列表，复制原表的所有公式
            val updatedRowFormulas = mutableListOf<String?>()
            repeat(baseTable.columnCount) { colIndex ->
                updatedRowFormulas.add(baseTable.getFormula(rowIndex, colIndex))
            }
            
            // 查找对应的更新行
            val key = baseRow[0] // 使用第一列作为键
            val updateRow = updateRowMap[key]
            
            if (updateRow != null) {
                val updateRowIndex = updateTable.rows.indexOf(updateRow)
                
                // 比较并更新不一致的列
                baseRow.forEachIndexed { colIndex, baseValue ->
                    val updateValue = updateRow[colIndex]
                    
                    // 调试输出
                    if (rowIndex < 3) { // 只输出前3行的调试信息
                        println("行$rowIndex, 列$colIndex: 基础值='$baseValue', 更新值='$updateValue'")
                    }
                    
                    // 如果更新值包含错误，跳过更新
                    if (updateValue.contains("#VALUE!") || updateValue.contains("#ERROR!") || 
                        updateValue.contains("VALUE") || updateValue.contains("ERROR")) {
                        return@forEachIndexed
                    }
                    
                    // 如果更新值不为空且与基础值不同，则更新
                    // 同时确保不会用空值覆盖有值的数据
                    // 避免更新为"VALUE"等异常文本
                    // 如果基础值是有意义的，而更新值是错误的，保持基础值
                    if (updateValue.isNotBlank() && updateValue != baseValue && 
                        !(baseValue.isNotBlank() && updateValue.isBlank()) &&
                        updateValue != "VALUE" && !updateValue.startsWith("#") &&
                        updateValue != "错误" && updateValue != "公式错误" &&
                        !updateValue.contains("VALUE") && !updateValue.contains("ERROR") &&
                        !(baseValue.isNotBlank() && (updateValue.startsWith("#") || updateValue.contains("VALUE")))) {
                        updatedRow[colIndex] = updateValue
                        changes.add(CellChange(
                            row = rowIndex,
                            column = colIndex,
                            originalValue = baseValue,
                            newValue = updateValue,
                            sourceTable = updateTable.fileName
                        ))
                        
                        // 检查是否有公式需要更新
                        val updateFormula = updateTable.getFormula(updateRowIndex, colIndex)
                        if (updateFormula != null) {
                            println("复制更新表格公式: 行$rowIndex, 列$colIndex, 公式=$updateFormula")
                            updatedRowFormulas[colIndex] = updateFormula
                        } else {
                            // 如果没有更新公式，保持原表的公式
                            val baseFormula = baseTable.getFormula(rowIndex, colIndex)
                            if (baseFormula != null) {
                                println("保持原表公式: 行$rowIndex, 列$colIndex, 公式=$baseFormula")
                                updatedRowFormulas[colIndex] = baseFormula
                            }
                        }
                    }
                }
            }
            
            updatedRows.add(updatedRow)
            updatedFormulas.add(updatedRowFormulas)
        }
        
        // 添加更新表格中存在但基础表格中不存在的行
        updateTable.rows.forEach { updateRow ->
            val key = updateRow[0]
            if (!baseRowMap.containsKey(key)) {
                val updateRowIndex = updateTable.rows.indexOf(updateRow)
                updatedRows.add(updateRow)
                
                val newRowFormulas = mutableListOf<String?>()
                repeat(baseTable.columnCount) { colIndex ->
                    newRowFormulas.add(updateTable.getFormula(updateRowIndex, colIndex))
                }
                updatedFormulas.add(newRowFormulas)
                
                changes.add(CellChange(
                    row = updatedRows.size - 1,
                    column = -1, // 表示新增行
                    originalValue = "",
                    newValue = "新增行",
                    sourceTable = updateTable.fileName
                ))
            }
        }
        
        val updatedTable = TableData(
            fileName = baseTable.fileName,
            headers = baseTable.headers,
            rows = updatedRows,
            formulas = updatedFormulas
        )
        
        return MergeResult(
            originalTable = baseTable,
            updatedTable = updatedTable,
            changes = changes
        )
    }
    
    private fun createRowMap(table: TableData): Map<String, List<String>> {
        return table.rows.associateBy { it[0] } // 使用第一列作为键
    }
    
    fun generateFileName(): String {
        val currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return "合并结果_${currentDate.format(formatter)}.xlsx"
    }
    
    fun getChangesSummary(changes: List<CellChange>): String {
        if (changes.isEmpty()) {
            return "没有发现需要更新的数据"
        }
        
        val cellChanges = changes.filter { it.column >= 0 }
        val newRows = changes.filter { it.column == -1 }
        
        return buildString {
            appendLine("合并完成！")
            appendLine("数据更新统计：")
            appendLine("- 单元格更新: ${cellChanges.size} 个")
            appendLine("- 新增行: ${newRows.size} 行")
            appendLine()
            appendLine("详细变更：")
            cellChanges.take(10).forEach { change ->
                appendLine("第${change.row + 1}行第${change.column + 1}列: '${change.originalValue}' → '${change.newValue}'")
            }
            if (cellChanges.size > 10) {
                appendLine("... 还有 ${cellChanges.size - 10} 个变更")
            }
        }
    }
}
