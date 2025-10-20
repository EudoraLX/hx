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

data class OrderRemovalResult(
    val originalTable: TableData,
    val filteredTable: TableData,
    val removedRows: List<List<String>>,
    val removedCount: Int,
    val remainingCount: Int
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
                    val baseFormula = baseTable.getFormula(rowIndex, colIndex)
                    
                    // 如果原表该列有公式，跳过该列的更新（保持公式计算）
                    if (baseFormula != null) {
                        // 保持原表的公式
                        updatedRowFormulas[colIndex] = baseFormula
                        return@forEachIndexed
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
                        
                        // 处理公式逻辑：如果更新表有公式，使用更新表的公式
                        val updateFormula = updateTable.getFormula(updateRowIndex, colIndex)
                        if (updateFormula != null) {
                            updatedRowFormulas[colIndex] = updateFormula
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
    
    /**
     * 移除已完成的订单
     * @param tableData 要处理的表格数据
     * @param plannedColumnIndex 计划发货数量列的索引
     * @param shippedColumnIndex 已发货数列的索引
     * @return 移除结果，包含过滤后的数据和被移除的行
     */
    fun removeCompletedOrders(tableData: TableData, 
                             plannedColumnIndex: Int, 
                             shippedColumnIndex: Int): OrderRemovalResult {
        val removedRows = mutableListOf<List<String>>()
        val filteredRows = mutableListOf<List<String>>()
        val filteredFormulas = mutableListOf<List<String?>>()
        
        tableData.rows.forEachIndexed { rowIndex, row ->
            val plannedValue = if (plannedColumnIndex < row.size) row[plannedColumnIndex] else "0"
            val shippedValue = if (shippedColumnIndex < row.size) row[shippedColumnIndex] else "0"
            
            // 尝试解析为数字进行比较
            val planned = plannedValue.toDoubleOrNull() ?: 0.0
            val shipped = shippedValue.toDoubleOrNull() ?: 0.0
            
            if (planned > 0 && planned == shipped) {
                // 计划数量等于已发货数量，视为已完成
                removedRows.add(row)
            } else {
                // 未完成，保留该行
                filteredRows.add(row)
                filteredFormulas.add(tableData.formulas[rowIndex])
            }
        }
        
        val filteredTable = TableData(
            fileName = tableData.fileName,
            headers = tableData.headers,
            rows = filteredRows,
            formulas = filteredFormulas
        )
        
        return OrderRemovalResult(
            originalTable = tableData,
            filteredTable = filteredTable,
            removedRows = removedRows,
            removedCount = removedRows.size,
            remainingCount = filteredRows.size
        )
    }
    
    /**
     * 根据条件移除行
     */
    fun removeRowsByCondition(tableData: TableData, 
                              columnIndex: Int, 
                              condition: String, 
                              value: String): OrderRemovalResult {
        val removedRows = mutableListOf<List<String>>()
        val filteredRows = mutableListOf<List<String>>()
        val filteredFormulas = mutableListOf<List<String?>>()
        
        tableData.rows.forEachIndexed { rowIndex, row ->
            val cellValue = if (columnIndex < row.size) row[columnIndex] else ""
            val shouldRemove = when (condition) {
                "等于" -> cellValue == value
                "不等于" -> cellValue != value
                "包含" -> cellValue.contains(value)
                "不包含" -> !cellValue.contains(value)
                "为空" -> cellValue.isBlank()
                "不为空" -> cellValue.isNotBlank()
                else -> false
            }
            
            if (shouldRemove) {
                removedRows.add(row)
            } else {
                filteredRows.add(row)
                filteredFormulas.add(tableData.formulas[rowIndex])
            }
        }
        
        val filteredTable = TableData(
            fileName = tableData.fileName,
            headers = tableData.headers,
            rows = filteredRows,
            formulas = filteredFormulas
        )
        
        return OrderRemovalResult(
            originalTable = tableData,
            filteredTable = filteredTable,
            removedRows = removedRows,
            removedCount = removedRows.size,
            remainingCount = filteredRows.size
        )
    }
    
    /**
     * 预览订单移除结果
     */
    fun previewOrderRemoval(tableData: TableData, 
                           plannedColumnIndex: Int, 
                           shippedColumnIndex: Int): String {
        val result = removeCompletedOrders(tableData, plannedColumnIndex, shippedColumnIndex)
        
        return buildString {
            appendLine("订单移除预览：")
            appendLine("原始订单数: ${tableData.rowCount}")
            appendLine("将移除订单数: ${result.removedCount}")
            appendLine("剩余订单数: ${result.remainingCount}")
            appendLine()
            
            if (result.removedRows.isNotEmpty()) {
                appendLine("将被移除的订单：")
                result.removedRows.take(5).forEachIndexed { index, row ->
                    val orderId = if (row.isNotEmpty()) row[0] else "未知"
                    val planned = if (plannedColumnIndex < row.size) row[plannedColumnIndex] else "0"
                    val shipped = if (shippedColumnIndex < row.size) row[shippedColumnIndex] else "0"
                    appendLine("  ${index + 1}. 序号: $orderId, 计划: $planned, 已发: $shipped")
                }
                if (result.removedRows.size > 5) {
                    appendLine("  ... 还有 ${result.removedRows.size - 5} 个订单")
                }
            }
        }
    }
    
    /**
     * 根据订单ID列表移除行
     */
    fun removeRowsByOrderIds(table: TableData, orderIds: List<String>): OrderRemovalResult {
        val filteredRows = mutableListOf<List<String>>()
        val filteredFormulas = mutableListOf<List<String?>>()
        val removedRows = mutableListOf<List<String>>()
        
        table.rows.forEachIndexed { rowIndex, row ->
            val orderId = if (row.isNotEmpty()) row[0] else ""
            
            if (orderId in orderIds) {
                // 移除这一行
                removedRows.add(row)
            } else {
                // 保留这一行
                filteredRows.add(row)
                filteredFormulas.add(table.formulas.getOrNull(rowIndex) ?: List(table.headers.size) { null })
            }
        }
        
        val filteredTable = TableData(
            fileName = table.fileName,
            headers = table.headers,
            rows = filteredRows,
            formulas = filteredFormulas
        )
        
        return OrderRemovalResult(
            originalTable = table,
            filteredTable = filteredTable,
            removedRows = removedRows,
            removedCount = removedRows.size,
            remainingCount = filteredRows.size
        )
    }
}
