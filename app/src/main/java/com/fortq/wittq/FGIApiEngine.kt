package com.fortq.wittq

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// FGApiEngine.kt - 새로 생성
object FGApiEngine {
    private const val FGI_URL = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata"

    suspend fun fetchFgiAll(): Pair<FearGreedData?, List<Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(FGI_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doInput = true

                    // 💡 실제 크롬 브라우저와 유사하게 헤더 구성
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9,ko;q=0.8")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("Referer", "https://www.cnn.com/markets/fear-and-greed")
                }

                val responseCode = connection.responseCode
                Log.d("WITTQ_FGI_DEBUG", "Response Code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("WITTQ_FGI_DEBUG", "HTTP error code: $responseCode")
                    return@withContext Pair(null, emptyList())
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("WITTQ_FGI_DEBUG", "Response received: ${response.take(200)}...")

                val jsonObj = JSONObject(response)

                // 1. 최신 데이터 파싱
                val fgObj = jsonObj.getJSONObject("fear_and_greed")
                val currentData = FearGreedData(
                    score = fgObj.getDouble("score"),
                    rating = fgObj.getString("rating")
                )

                Log.d("WITTQ_FGI_DEBUG", "Current F&G: ${currentData.score} - ${currentData.rating}")

                // 2. 히스토리 데이터 파싱 (90일치)
                val historicalObj = jsonObj.getJSONObject("fear_and_greed_historical")
                val dataArray = historicalObj.getJSONArray("data")
                val historyList = mutableListOf<Double>()
                val startIdx = (dataArray.length() - 90).coerceAtLeast(0)
                for (i in startIdx until dataArray.length()) {
                    // historical 데이터 내 요소는 {"x": timestamp, "y": score} 형태임
                    val score = dataArray.getJSONObject(i).getDouble("y")
                    historyList.add(score)
                }

                Pair(currentData as FearGreedData?, historyList.toList())

            } catch (e: Exception) {
                Log.e("WITTQ_FGI_DEBUG", "Failed to fetch Fear & Greed: ${e.message}")
                Pair(null, emptyList())
            }
        }
    }

    /*suspend fun fetchFearGreedHistory(days: Int = 90): List<Double> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://production.dataviz.cnn.io/index/fearandgreed/graphdata")
                val connection = url.openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val jsonObj = JSONObject(response)
                val dataArray = jsonObj.getJSONArray("fear_and_greed_historical") // 히스토리 데이터 추출

                val history = mutableListOf<Double>()
                val startIdx = (dataArray.length() - days).coerceAtLeast(0)
                for (i in startIdx until dataArray.length()) {
                    history.add(dataArray.getJSONObject(i).getDouble("score"))
                }
                history
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun parseFearGreedJson(json: String): FearGreedData? {
        try {
            val jsonObj = JSONObject(json)
            val score = jsonObj.getJSONObject("fear_and_greed")
                .getDouble("score")
            val rating = jsonObj.getJSONObject("fear_and_greed")
                .getString("rating")

            return FearGreedData(score, rating)
        } catch (e: Exception) {
            Log.e("WITTQ_FGI_DEBUG", "Parse error: ${e.message}")
            return null
        }
    }*/

    suspend fun fetchPutCallRatio(): Double? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("WITTQ_FGI_DEBUG", "Fetching ^CPCS starting...")
                val prices = StockApiEngine.fetchPrices("^PCQQ")
                if (prices.isEmpty()) {
                    Log.e("WITTQ_FGI_DEBUG", "^CPCS data is EMPTY from StockApiEngine")
                    return@withContext null
                }

                val ratio = prices.lastOrNull()
                Log.d("WITTQ_FGI_DEBUG", "Put/Call Ratio: $ratio")
                ratio
            } catch (e: Exception) {
                Log.e("WITTQ_FGI_DEBUG", "Failed to fetch Put/Call: ${e.message}")
                null
            }
        }
    }
}

data class FearGreedData(
    val score: Double,  // 0-100
    val rating: String  // "Extreme Fear", "Fear", "Neutral", "Greed", "Extreme Greed"
)