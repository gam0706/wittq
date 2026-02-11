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

    suspend fun fetchAll(): FullData? {
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
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("WITTQ_FGI_DEBUG", "Response received: ${response.take(200)}...")

                val jsonObj = JSONObject(response)

                // 1. 최신 데이터 파싱
                val fgObj = jsonObj.getJSONObject("fear_and_greed")
                val fgData = FearGreedData(
                    score = fgObj.getDouble("score"),
                    rating = fgObj.getString("rating")
                )

                val pcRatio = try {
                    val pcDataArray = jsonObj.getJSONObject("put_call_options").getJSONArray("data")
                    if (pcDataArray.length() > 0) {
                        pcDataArray.getJSONObject(pcDataArray.length() - 1).getDouble("y")
                    } else {
                        0.85
                    }
                } catch (e: Exception) {
                    Log.e("WITTQ_FGI_DEBUG", "PC Ratio 파싱 실패, 기본값 사용 : ${e.message}" )
                    0.85 // 파싱 실패 시 중립값
                }

                Log.d("WITTQ_FGI_DEBUG", "Current F&G: ${fgData.score} - ${fgData.rating}")

                // 2. 히스토리 데이터 파싱 (90일치)
                val historicalObj = jsonObj.getJSONObject("fear_and_greed_historical")
                val fgdataArray = historicalObj.getJSONArray("data")

                val historyList = mutableListOf<Double>()
                val totalCount = fgdataArray.length()
                Log.d("WITTQ_FGI_DEBUG", "Total data count: $totalCount")

                val startIdx = (totalCount - 90).coerceAtLeast(0)

                for (i in startIdx until totalCount) {
                    val fgval = fgdataArray.getJSONObject(i).optDouble("y")
                    historyList.add(fgval)
                }

                Log.d("WITTQ_FGI_DEBUG", "Fetch Success: FG=${fgData.score}, PC=$pcRatio, History=${historyList.size}")
                Log.d("WITTQ_FGI_DEBUG", "History First: ${historyList.firstOrNull()}, Last: ${historyList.lastOrNull()}")
                Log.d("WITTQ_FGI_DEBUG", "First 5: ${historyList.take(5)}")
                Log.d("WITTQ_FGI_DEBUG", "Last 5: ${historyList.takeLast(5)}")

                FullData(fgData, historyList, pcRatio)

            } catch (e: Exception) {
                Log.e("WITTQ_FGI_DEBUG", "Failed to fetch Fear & Greed: ${e.message}")
                null
            }
        }
    }
}
data class FearGreedData(val score: Double, val rating: String)

data class FullData(
    val fgData: FearGreedData,
    val fgHistory: List<Double>,
    val pcRatio: Double
)