package com.fortq.wittq

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class YahooResponse(val chart: YahooChart)
data class YahooChart(val result: List<YahooResultData>?)
data class YahooResultData(val indicators: YahooIndicators)
data class YahooIndicators(val quote: List<YahooQuote>)
data class YahooQuote(val close: List<Double?>)

interface YahooApiService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getHistory(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "2y"
    ): YahooResponse
}

object StockApiEngine {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: YahooApiService = retrofit.create(YahooApiService::class.java)

    suspend fun fetchPrices(symbol: String): List<Double> {
        return try {
            val response = service.getHistory(symbol)
            response.chart.result?.get(0)?.indicators?.quote?.get(0)?.close
                ?.filterNotNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}