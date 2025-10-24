package io.github.kotlin.fibonacci.core

import io.github.kotlin.fibonacci.model.PipeSpecification

/**
 * 管规格解析器
 * 解析复杂的管规格字符串格式
 */
class PipeSpecParser {
    
    /**
     * 解析管规格字符串
     * 支持格式：
     * - "Ø 130/154-Ø 204/226" (范围)
     * - "Ø 102、Ø 113、Ø 120/Ø 137" (多个规格)
     * - "Ø 90" (单个规格)
     * - "Ø 180、Ø 200/Ø 217 (大)" (带大标记)
     */
    fun parsePipeSpecs(specString: String): List<PipeSpecification> {
        val specs = mutableListOf<PipeSpecification>()
        
        if (specString.isBlank()) return specs
        
        // 移除Ø符号和空格，但保留结构
        val cleanSpec = specString.replace("Ø", "").replace(" ", "")
        
        // 检查是否为大规格
        val isLarge = cleanSpec.contains("(大)")
        val hasCones = cleanSpec.contains("小锥") || cleanSpec.contains("大锥")
        val coneType = when {
            cleanSpec.contains("小锥") -> "小锥"
            cleanSpec.contains("大锥") -> "大锥"
            else -> ""
        }
        
        // 移除特殊标记
        val specWithoutMarks = cleanSpec.replace("(大)", "").replace("小锥", "").replace("大锥", "")
        
        // 解析各种格式
        when {
            // 范围格式：130/154-204/226 或 102/122-195/215
            specWithoutMarks.contains("-") -> {
                val parts = specWithoutMarks.split("-")
                if (parts.size == 2) {
                    // 解析第一部分：102/122 -> 内径102, 外径122
                    val startSpec = parseSingleSpec(parts[0])
                    // 解析第二部分：195/215 -> 内径195, 外径215
                    val endSpec = parseSingleSpec(parts[1])
                    if (startSpec != null && endSpec != null) {
                        specs.add(PipeSpecification(
                            innerDiameter = startSpec.first,
                            outerDiameter = startSpec.second,
                            isLarge = isLarge,
                            hasCones = hasCones,
                            coneType = coneType
                        ))
                        specs.add(PipeSpecification(
                            innerDiameter = endSpec.first,
                            outerDiameter = endSpec.second,
                            isLarge = isLarge,
                            hasCones = hasCones,
                            coneType = coneType
                        ))
                    }
                }
            }
            // 多个规格：102、113、120/137
            specWithoutMarks.contains("、") -> {
                val parts = specWithoutMarks.split("、")
                parts.forEach { part ->
                    val spec = parseSingleSpec(part)
                    if (spec != null) {
                        specs.add(PipeSpecification(
                            innerDiameter = spec.first,
                            outerDiameter = spec.second,
                            isLarge = isLarge,
                            hasCones = hasCones,
                            coneType = coneType
                        ))
                    }
                }
            }
            // 单个规格：90
            else -> {
                val spec = parseSingleSpec(specWithoutMarks)
                if (spec != null) {
                    specs.add(PipeSpecification(
                        innerDiameter = spec.first,
                        outerDiameter = spec.second,
                        isLarge = isLarge,
                        hasCones = hasCones,
                        coneType = coneType
                    ))
                }
            }
        }
        
        return specs
    }
    
    /**
     * 解析单个规格
     */
    private fun parseSingleSpec(spec: String): Pair<Double, Double>? {
        return when {
            // 格式：130/154 (内径/外径)
            spec.contains("/") -> {
                val parts = spec.split("/")
                if (parts.size == 2) {
                    val inner = parts[0].toDoubleOrNull()
                    val outer = parts[1].toDoubleOrNull()
                    if (inner != null && outer != null) {
                        return Pair(inner, outer)
                    }
                }
                null
            }
            // 格式：90 (只有外径，内径设为0表示未指定)
            else -> {
                val outer = spec.toDoubleOrNull()
                if (outer != null) {
                    // 对于只有外径的情况，内径设为0表示未指定
                    return Pair(0.0, outer)
                }
                null
            }
        }
    }
    
    /**
     * 根据外径推算内径
     */
    private fun calculateInnerDiameter(outerDiameter: Double): Double {
        // 根据实际业务规则推算内径
        // 这里需要根据您的具体业务规则来调整
        return when {
            outerDiameter <= 150 -> outerDiameter - 20
            outerDiameter <= 300 -> outerDiameter - 30
            else -> outerDiameter - 40
        }
    }
    
    /**
     * 检查管规格是否匹配
     */
    fun isPipeSpecMatch(orderSpec: PipeSpecification, ruleSpec: PipeSpecification): Boolean {
        // 精确匹配
        if (orderSpec.innerDiameter == ruleSpec.innerDiameter && 
            orderSpec.outerDiameter == ruleSpec.outerDiameter) {
            return true
        }
        
        // 范围匹配（如果规则定义了范围）
        // 这里需要根据实际的管规格格式来实现范围匹配逻辑
        val innerDiff = kotlin.math.abs(orderSpec.innerDiameter - ruleSpec.innerDiameter)
        val outerDiff = kotlin.math.abs(orderSpec.outerDiameter - ruleSpec.outerDiameter)
        
        // 扩大误差范围以支持更多规格匹配（±10mm）
        // 这样可以匹配更多订单到合适的机台
        return innerDiff <= 10.0 && outerDiff <= 10.0
    }
    
    /**
     * 智能匹配管规格
     * 支持范围匹配和近似匹配
     * 根据机台模具管规格表进行精确匹配
     */
    fun smartPipeSpecMatch(orderSpec: PipeSpecification, ruleSpec: PipeSpecification): Boolean {
        // 1. 精确匹配
        if (orderSpec.innerDiameter == ruleSpec.innerDiameter && 
            orderSpec.outerDiameter == ruleSpec.outerDiameter) {
            return true
        }
        
        // 2. 范围匹配（处理102/195, 180/330等范围规格）
        val innerDiff = kotlin.math.abs(orderSpec.innerDiameter - ruleSpec.innerDiameter)
        val outerDiff = kotlin.math.abs(orderSpec.outerDiameter - ruleSpec.outerDiameter)
        
        // 3. 根据外径大小调整匹配精度
        val tolerance = when {
            orderSpec.outerDiameter <= 150 -> 5.0  // 小规格，精确匹配
            orderSpec.outerDiameter <= 300 -> 10.0  // 中规格，中等精度
            orderSpec.outerDiameter <= 500 -> 15.0  // 大规格，宽松匹配
            else -> 20.0  // 超大规格，最宽松匹配
        }
        
        return innerDiff <= tolerance && outerDiff <= tolerance
    }
    
    /**
     * 精确匹配管规格
     * 只看外径的匹配规则：
     * 1. 外径匹配即可，完全忽略内径
     * 2. 外径相同或相近就匹配成功
     * 3. 内径可以任意，不需要考虑
     * 4. 扩大匹配范围，确保更多订单能匹配到机台
     */
    fun exactPipeSpecMatch(orderSpec: PipeSpecification, ruleSpec: PipeSpecification): Boolean {
        // 只看外径，外径相同或相近就匹配成功
        val outerDiff = kotlin.math.abs(orderSpec.outerDiameter - ruleSpec.outerDiameter)
        
        // 扩大外径误差范围：±20mm，确保更多订单能匹配到机台
        return outerDiff <= 20.0
    }
    
    /**
     * 从客户型号解析内外径
     * 例如：FH-550/12.5-4295 -> 内径12.5, 外径550
     */
    fun parseFromCustomerModel(customerModel: String): PipeSpecification? {
        if (customerModel.isBlank()) return null
        
        // 匹配格式：FH-550/12.5-4295
        val pattern = Regex("FH-(\\d+)/(\\d+(?:\\.\\d+)?)-(\\d+)")
        val match = pattern.find(customerModel)
        
        if (match != null) {
            val outerDiameter = match.groupValues[1].toDoubleOrNull() ?: return null
            val innerDiameter = match.groupValues[2].toDoubleOrNull() ?: return null
            
            return PipeSpecification(
                innerDiameter = innerDiameter,
                outerDiameter = outerDiameter,
                isLarge = outerDiameter > 400,
                hasCones = false,
                coneType = ""
            )
        }
        
        return null
    }
    
    /**
     * 测试管规格解析
     */
    fun testPipeSpecParsing() {
        val testSpec = "Ø 102/Ø 122-Ø 195/Ø 215"
        println("测试规格: $testSpec")
        
        val specs = parsePipeSpecs(testSpec)
        specs.forEach { spec ->
            println("解析结果: 内径=${spec.innerDiameter}, 外径=${spec.outerDiameter}")
        }
    }
}
