package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.*

class TableMerger {
    
    fun canMergeTables(tables: List<TableData>): Boolean {
        if (tables.isEmpty()) return false
        if (tables.size == 1) return true
        
        val firstHeaders = tables[0].headers
        return tables.all { table ->
            table.headers.size == firstHeaders.size &&
            table.headers.zip(firstHeaders).all { (h1, h2) -> h1 == h2 }
        }
    }
    
    fun mergeTables(tables: List<TableData>): MergedTableData? {
        if (tables.isEmpty()) return null
        if (!canMergeTables(tables)) return null
        
        val allRows = mutableListOf<List<String>>()
        val allFormulas = mutableListOf<List<String?>>()
        
        tables.forEach { table ->
            allRows.addAll(table.rows)
            allFormulas.addAll(table.formulas)
        }
        
        return MergedTableData(
            sourceTables = tables,
            headers = tables[0].headers,
            rows = allRows,
            formulas = allFormulas
        )
    }
    
    fun getMergeValidationMessage(tables: List<TableData>): String {
        if (tables.isEmpty()) return "没有可合并的表格"
        if (tables.size == 1) return "只有一个表格，无需合并"
        
        val firstTable = tables[0]
        val mismatchedTables = mutableListOf<String>()
        
        tables.forEachIndexed { index, table ->
            if (table.headers.size != firstTable.headers.size) {
                mismatchedTables.add("${table.fileName} (列数不匹配: ${table.headers.size} vs ${firstTable.headers.size})")
            } else if (!table.headers.zip(firstTable.headers).all { (h1, h2) -> h1 == h2 }) {
                mismatchedTables.add("${table.fileName} (列标题不匹配)")
            }
        }
        
        return if (mismatchedTables.isEmpty()) {
            "所有表格结构一致，可以合并"
        } else {
            "以下表格结构不匹配，无法合并:\n${mismatchedTables.joinToString("\n")}"
        }
    }
}
