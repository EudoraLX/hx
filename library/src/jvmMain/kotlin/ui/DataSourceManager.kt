package io.github.kotlin.fibonacci.ui

import io.github.kotlin.fibonacci.model.*
import javax.swing.*

/**
 * 数据源管理器
 * 管理多个数据源并提供切换功能
 */
class DataSourceManager {
    
    private val dataSources = mutableMapOf<String, TableData>()
    
    /**
     * 添加数据源
     */
    fun addDataSource(name: String, tableData: TableData) {
        dataSources[name] = tableData
    }
    
    /**
     * 获取数据源
     */
    fun getDataSource(name: String): TableData? {
        return dataSources[name]
    }
    
    /**
     * 获取所有数据源名称
     */
    fun getDataSourceNames(): List<String> {
        return dataSources.keys.toList()
    }
    
    /**
     * 移除数据源
     */
    fun removeDataSource(name: String) {
        dataSources.remove(name)
    }
    
    /**
     * 清空所有数据源
     */
    fun clearAll() {
        dataSources.clear()
    }
    
    /**
     * 获取数据源信息
     */
    fun getDataSourceInfo(name: String): String? {
        val tableData = dataSources[name] ?: return null
        return "${tableData.fileName} (${tableData.rowCount} 行, ${tableData.columnCount} 列)"
    }
    
    /**
     * 检查数据源是否存在
     */
    fun hasDataSource(name: String): Boolean {
        return dataSources.containsKey(name)
    }
}
