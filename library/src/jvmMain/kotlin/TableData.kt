package io.github.kotlin.fibonacci

data class TableData(
    val fileName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val formulas: List<List<String?>> = emptyList()
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = headers.size
    
    fun getValue(row: Int, col: Int): String {
        return if (row < rows.size && col < rows[row].size) {
            rows[row][col]
        } else ""
    }
    
    fun getFormula(row: Int, col: Int): String? {
        return if (row < formulas.size && col < formulas[row].size) {
            formulas[row][col]
        } else null
    }
}

data class MergedTableData(
    val sourceTables: List<TableData>,
    val headers: List<String>,
    val rows: List<List<String>>,
    val formulas: List<List<String?>> = emptyList()
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = headers.size
    
    fun getValue(row: Int, col: Int): String {
        return if (row < rows.size && col < rows[row].size) {
            rows[row][col]
        } else ""
    }
    
    fun getFormula(row: Int, col: Int): String? {
        return if (row < formulas.size && col < formulas[row].size) {
            formulas[row][col]
        } else null
    }
}
