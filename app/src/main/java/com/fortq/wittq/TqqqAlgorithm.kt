package com.fortq.wittq

import kotlin.math.pow
import kotlin.math.sqrt

data class AlgoResult(
    val score: Int,
    val marketStatus: String,
    val actionTitle: String,
    val actionDesc: String,
    val actionColor: Long,
    val disparity: Double,
    val vol20: Double,
    val isTqqqBullish: Boolean,
    val isQqqBullish: Boolean,
    val displayPosition: String,
    val userPosition: String,
    val currentPrice: Double,
    val profitRate: Double,
    val avgPrice: Double
)

// AGTQ Data
data class AGTResult(
    val tq200: Double,
    val agtscore: Int,
    val tqqqPrice: Double,
    val tqqqPrevClose: Double,
    val envHigh: Double,
    val stopLoss: Double,
    val agtsignal: String,
    val agtaction: String,
    val agtColor: Long,
    val isbull: Boolean,
    val isbear: Boolean
)

object AGTQStrategy {
    fun calc(tqPrice: List<Double>): AGTResult {
        val bullColor = 0xFF30D158
        val bearColor = 0xFFFF453A
        val grayColor = 0xFF8E8E93
        val purpleColor = 0xFFBF5AF2

        val tqCurrent = tqPrice.lastOrNull() ?: 0.0
        val tqPrev = if (tqPrice.size >= 2) tqPrice[tqPrice.size - 2] else tqCurrent
        val tq200 = tqPrice.takeLast(200).average()
        val envHigh = tq200 * 1.05
        val stopLoss = tq200 * 0.95
        var agtscore = 0
        if (tqPrev >= tq200) agtscore += 1
        if (tqPrev >= tq200 && tqCurrent >= tq200) agtscore += 1
        if (tqCurrent < tq200) agtscore = 0
        val isbull = agtscore >= 1
        val isbear = agtscore == 0

        val (agtsignal, agtaction, agtColor) = when {
            // MA200 -5% 도달 또는 MA200 이탈 시 약세
            tqCurrent <= stopLoss || tqCurrent < tq200 ->
                Triple("Bearish", "ALL SGOV", bearColor) // RED

            // MA200 < 종가 < 엔벨로프 상단 [집중매수]
            tqCurrent >= tq200 && tqCurrent <= envHigh -> {
                if (agtscore == 2) Triple("STRONG", "ALL TQQQ", bullColor) // GREEN
                else if (agtscore == 1) Triple("FOCUS", "ALL TQQQ", bullColor)
                else Triple("WAIT", "STAY SGOV", grayColor)
            }

            tqCurrent > envHigh ->
                Triple("OVERWEIGHT", "BUY SPY", purpleColor) // purple

            else -> Triple("ERROR", "-", grayColor)
        }

        return AGTResult(
            tq200 = tq200,
            agtscore = agtscore,
            tqqqPrice = tqCurrent,
            tqqqPrevClose = tqPrev,
            envHigh = envHigh,
            stopLoss = stopLoss,
            agtsignal = agtsignal,
            agtaction = agtaction,
            agtColor = agtColor,
            isbull = isbull,
            isbear = isbear
        )
    }
}


object TqqqAlgorithm {
    fun calculate(
        qPrices: List<Double>,
        tPrices: List<Double>,
        qldPrices: List<Double>,
        sgovPrices: List<Double>,
        userPosition: String,
        avgPrice: Double = 50.0
    ): AlgoResult {
        val qqqMA3 = qPrices.takeLast(3).average()
        val qqqMA161 = qPrices.takeLast(161).average()
        val tqqqCurrent = tPrices.lastOrNull() ?: 0.0
        val qldCurrent = qldPrices.lastOrNull() ?: 0.0
        val sgovCurrent = sgovPrices.lastOrNull() ?: 0.0
        val tqqqMA200 = tPrices.takeLast(200).average()
        val disparity = (tqqqCurrent / tqqqMA200) * 100
        val vol20 = calculateVolatility(tPrices, 20)

        // 스코어 및 상태 [cite: 92]
        val isQqqBullish = qqqMA3 > qqqMA161
        val isTqqqBullish = tqqqCurrent > tqqqMA200
        var score = 0
        if (isQqqBullish) score += 1
        if (isTqqqBullish) score += 1
        val marketStatus = when(score) { 2 -> "TQQQ"; 1 -> "QLD"; else -> "CASH" }
        val isOverheated = disparity >= 151
        val isVolatilityRisk = vol20 >= 6.2

        var actionTitle: String
        var actionDesc: String
        var actionColor: Long = 0xFF8E8E93
        var finalDisplayPos = userPosition
        var finalAvgPrice = if (finalDisplayPos.uppercase() == "CASH") 0.0 else avgPrice
        var finalCurrentPrice = when(userPosition) {
            "TQQQ" -> tqqqCurrent
            "QLD" -> qldCurrent
            "CASH" -> sgovCurrent
            else -> 0.0
        }

        val bullColor = 0xFF30D158
        val bearColor = 0xFFFF453A
        val grayColor = 0xFF8E8E93
        val purpleColor = 0xFFBF5AF2

        // 데이터 부족 예외 처리 [cite: 88]
        if (qPrices.size < 262 || tPrices.size < 200) {
            return AlgoResult(0, "DATA ERROR", "WAIT", "Loading...", 0xFF8E8E93, 0.0, 0.0,
                isTqqqBullish = false, isQqqBullish = false, "WAIT", userPosition, tqqqCurrent, 0.0, avgPrice)
        }

        when {
            isOverheated && isVolatilityRisk -> {
                finalDisplayPos = "SOLD 🧯"
                if (disparity <= 118) {
                    actionTitle = "RE-ENTER"
                    actionDesc = "Safe"
                    actionColor = bullColor
                } else {
                    actionTitle = "WAIT"
                    actionDesc = "Cooling ${String.format("%.0f", disparity)}%"
                    actionColor = grayColor
                }
            }
            // [B] 단일 리스크 조건
            isOverheated -> {
                actionTitle = "SELL"
                actionDesc = "Overheat"
                actionColor = purpleColor
            }
            isVolatilityRisk -> {
                actionTitle = "ESCAPE"
                actionDesc = "Volatility"
                actionColor = bearColor
            }
            // [C] 시장 상태별 포지션 매칭 로직
            marketStatus == "TQQQ" -> {
                finalDisplayPos = userPosition
                when (userPosition) {
                    "TQQQ" -> { actionTitle = "HOLD"; actionDesc = "Trending"; actionColor = bullColor }
                    "QLD" -> { actionTitle = "SWITCH"; actionDesc = "To TQQQ"; actionColor = bullColor }
                    else -> { actionTitle = "WAIT"; actionDesc = "Too LATE"; actionColor = grayColor }
                }
            }
            marketStatus == "QLD" -> {
                finalDisplayPos = userPosition
                when (userPosition) {
                    "TQQQ" -> { actionTitle = "SWITCH"; actionDesc = "To QLD"; actionColor = bullColor }
                    "QLD" -> { actionTitle = "HOLD"; actionDesc = "Trending"; actionColor = bullColor }
                    else -> { actionTitle = "WAIT"; actionDesc = "Too LATE"; actionColor = grayColor }
                }
            }
            else -> { // marketStatus == "CASH"
                finalDisplayPos = userPosition
                if (userPosition != "CASH") {
                    actionTitle = "SELL"; actionDesc = "Bearish"; actionColor = bearColor
                } else {
                    actionTitle = "WAIT"; actionDesc = "Bearish"; actionColor = grayColor
                }
            }
        }

        val profitRate = if (finalAvgPrice > 0) ((finalCurrentPrice - finalAvgPrice) / finalAvgPrice) * 100 else 0.0

        return AlgoResult(
            score = score,
            marketStatus = marketStatus,
            actionTitle = actionTitle,
            actionDesc = actionDesc,
            actionColor = actionColor,
            disparity = disparity,
            vol20 = vol20,
            isTqqqBullish = isTqqqBullish,
            isQqqBullish = isQqqBullish,
            displayPosition = finalDisplayPos,
            userPosition = userPosition,
            profitRate = profitRate,
            avgPrice =  finalAvgPrice,
            currentPrice = finalCurrentPrice
        )
    }

    private fun calculateVolatility(prices: List<Double>, n: Int): Double {
        if (prices.size < n + 1) return 0.0
        val returns = mutableListOf<Double>()
        for (i in prices.size - n until prices.size) {
            returns.add((prices[i] - prices[i - 1]) / prices[i - 1] * 100)
        }
        val mean = returns.average()
        return sqrt(returns.sumOf { (it - mean).pow(2) } / returns.size)
    }
}
