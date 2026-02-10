package com.fortq.wittq

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.core.graphics.createBitmap
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import java.text.SimpleDateFormat
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import kotlin.Double
import kotlin.collections.emptyList


class UpdatefgiCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            Log.d("WITTQ_FGI_DEBUG", "Refresh button clicked")
            FGIWidget().updateAll(context)
            Log.d("WITTQ_FGI_DEBUG", "Widget Refresh completed")
        } catch (e: Exception) {
            Log.e("WITTQ_FGI_DEBUG", "Widget Refresh failed: ${e.message}", e)
        }
    }
}

class FGIWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(300.dp, 100.dp), DpSize(412.dp, 150.dp))
    )

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("WITTQ_FGI_DEBUG", "Starting FGI widget update...")

        val resultData = withContext(Dispatchers.IO) {
            try {
                val (fgData, fgHistory) = FGApiEngine.fetchFgiAll()
                Log.d("WITTQ_FGI_DEBUG", "FG Data: $fgData, History size: ${fgHistory.size}")
                val pcRatio = FGApiEngine.fetchPutCallRatio()
                Log.d("WITTQ_FGI_DEBUG", "PC Ratio: $pcRatio")

                if (fgData != null && pcRatio != null && fgHistory.isNotEmpty()) {
                    // 타입 불일치 해결: 90일 히스토리(List), 현재가(Double), 색상 등을 정확히 전달
                    val fgChart = drawFearGreedChart(fgHistory)
                    val fgGauge = drawFearGreedGauge(fgData.score, 200)
                    val pcGauge = drawPutCallGauge(pcRatio, 200)

                    Log.d("WITTQ_FGI_DEBUG", "Charts created successfully")
                    FGResult(fgData, pcRatio, fgChart, fgGauge, pcGauge)
                } else {
                    Log.e("WITTQ_FGI_DEBUG", "Data incomplete - FG: $fgData, PC: $pcRatio, History: ${fgHistory.size}")
                    null
                }
            } catch (e: Exception) {
                Log.e("WITTQ_FGI_DEBUG", "Widget update failed", e)
                null
            }
        }

        provideContent {
            val size = LocalSize.current
            if (resultData != null) {
                FGWidgetUI(
                    resultData.fgData,
                    resultData.pcRatio,
                    resultData.fgChart,
                    resultData.fgGauge,
                    resultData.pcGauge,
                    size
                )
            } else {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Loading F&G Index...", style = TextStyle(color = ColorProvider(Color.White)))
                        val reftime =
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        Text(
                            reftime, style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.6f)),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    }

    data class FGResult(
        val fgData: FearGreedData,
        val pcRatio: Double,
        val fgChart: Bitmap,
        val fgGauge: Bitmap,
        val pcGauge: Bitmap
    )
    private fun drawFearGreedGauge(score: Double, size: Int): Bitmap {
        val bitmap = createBitmap(size, size / 2 + 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2f - 40f

        // 배경 반원 (회색)
        val bgPaint = Paint().apply {
            color = 0xFF2C2C2E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 30f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        val rectF = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        canvas.drawArc(rectF, 180f, 180f, false, bgPaint)

        // 구간별 색상 그리기
        val segments = listOf(
            Pair(0f, 25f) to 0xFFFF453A.toInt(),      // Extreme Fear (빨강)
            Pair(25f, 45f) to 0xFFFF9500.toInt(),     // Fear (주황)
            Pair(45f, 55f) to 0xFFFFCC00.toInt(),     // Neutral (노랑)
            Pair(55f, 75f) to 0xFF32D74B.toInt(),     // Greed (초록)
            Pair(75f, 100f) to 0xFF30D158.toInt()     // Extreme Greed (진한 초록)
        )

        segments.forEach { (range, color) ->
            val segmentPaint = Paint().apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 30f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            val startAngle = 180f + (range.first * 1.8f)
            val sweepAngle = (range.second - range.first) * 1.8f
            canvas.drawArc(rectF, startAngle, sweepAngle, false, segmentPaint)
        }

        // 바늘 그리기
        val needleAngle = 180f + (score * 1.8f).toFloat()
        val needlePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            strokeWidth = 8f
            isAntiAlias = true
        }

        val needleLength = radius - 20f
        val needleEndX = centerX + needleLength * Math.cos(Math.toRadians(needleAngle.toDouble())).toFloat()
        val needleEndY = centerY + needleLength * Math.sin(Math.toRadians(needleAngle.toDouble())).toFloat()

        canvas.drawLine(centerX, centerY, needleEndX, needleEndY, needlePaint)

        // 중심 원
        val centerPaint = Paint().apply {
            color = 0xFF1C1C1E.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, 15f, centerPaint)

        // 텍스트 레이블
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("EXTREME\nFEAR", centerX - radius + 60, centerY + 40, textPaint)
        canvas.drawText("NEUTRAL", centerX, centerY - radius - 10, textPaint)
        canvas.drawText("EXTREME\nGREED", centerX + radius - 60, centerY + 40, textPaint)

        return bitmap
    }

    private fun drawPutCallGauge(
        ratio: Double,
        size: Int
    ): Bitmap {
        val bitmap = createBitmap(size, size / 2 + 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rectF = RectF(40f, 40f, size.toFloat() - 40f, size.toFloat() - 40f)
        val bgPaint = Paint().apply {
            color = 0xFF2C2C2E.toInt();
            style = Paint.Style.STROKE;
            strokeWidth = 25f;
            isAntiAlias = true;
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rectF, 180f, 180f, false, bgPaint)

        val colorPaint = Paint().apply {
            this.color = getPCColor(ratio).toArgb();
            style = Paint.Style.STROKE;
            strokeWidth = 25f;
            isAntiAlias = true;
            strokeCap = Paint.Cap.ROUND
        }
        val angle = ((ratio - 0.5) / 1.0 * 180f).coerceIn(0.0, 180.0).toFloat()

        canvas.drawArc(rectF, 180f, angle, false, colorPaint)

        return bitmap
    }

    private fun drawFearGreedChart(
        fgindex: List<Double>,
    ): Bitmap {
        val width = 400
        val height = 300
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val indexPaint = Paint().apply {
            this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
        }
        val guidePaint = Paint().apply {
            this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f; alpha = 90
        }
        val textPaint = Paint().apply {
            this.color = android.graphics.Color.WHITE; textSize = 16f; isAntiAlias = true
        }

        if (fgindex.isEmpty()) return bitmap

        fun getY(v: Double) = (height - (v / 100.0 * height)).toFloat()

        val guideValues = listOf(25.0, 50.0, 75.0)
        guideValues.forEach {v ->
            val y = getY(v)
            canvas.drawLine(0f, y, width.toFloat(), y, guidePaint)
            canvas.drawText(v.toInt().toString(), 10f, y - 5f, textPaint)
        }
        if (fgindex.size >=2) {
            val path = android.graphics.Path()
            fgindex.forEachIndexed { i, p ->
                val x = i.toFloat() * (width.toFloat() / (fgindex.size - 1).coerceAtMost(1))
                val y = getY(p)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, indexPaint)
        }
        return bitmap
    }


    @SuppressLint("RestrictedApi")
    @Composable
    fun FGWidgetUI(
        fgData: FearGreedData,
        pcRatio: Double,
        fgChart: Bitmap,
        fgGauge: Bitmap,
        pcGauge: Bitmap,
        size: DpSize
    ) {
        val factor = (size.width.value / 400f).coerceIn(0.6f, 1.0f)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
                .cornerRadius(34.dp)
                .padding((20 * factor).dp)
        ) {
            Row(modifier = GlanceModifier.fillMaxSize()) {
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Fear & Greed Index
                        Text(
                            "Fear & Greed Index",
                            style = TextStyle(
                                fontSize = (16 * factor).sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color.White)
                            )
                        )

                        Spacer(modifier = GlanceModifier.height((8 * factor).dp))

                        Image(
                            provider = ImageProvider(fgChart),
                            contentDescription = "Fear Greed Chart",
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width((12 * factor).dp))

                Column( modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally)
                        {
                            Image(
                                provider = ImageProvider(pcGauge),
                                contentDescription = "Put Call Gauge",
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height((80 * factor).dp)
                            )
                            Text(
                                String.format("%.2f", pcRatio),
                                style = TextStyle(
                                    fontSize = (24 * factor).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(getPCColor(pcRatio))
                                )
                            )

                            Text(
                                getPCLabel(pcRatio),
                                style = TextStyle(
                                    fontSize = (11 * factor).sp,
                                    color = ColorProvider(Color.Gray)
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height((8 * factor).dp))

                    Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally)
                        {
                            Image(
                                provider = ImageProvider(fgGauge),
                                contentDescription = "Fear Greed Gauge",
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height((80 * factor).dp)
                            )

                            Text(
                                fgData.score.toInt().toString(),
                                style = TextStyle(
                                    fontSize = (24 * factor).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(getFGColor(fgData.score))
                                )
                            )

                            Text(
                                fgData.rating,
                                style = TextStyle(
                                    fontSize = (11 * factor).sp,
                                    color = ColorProvider(Color.Gray))
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getPCLabel(ratio: Double): String {
        return when {
            ratio < 0.62 -> "Overheat"
            ratio < 0.76 -> "Bullish"
            ratio < 0.93 -> "Neutral"
            else -> "Bearish"
        }
    }
    private fun getFGColor(score: Double): Color {
        return when {
            score < 25 -> Color(0xFFFF453A)    // Extreme Fear
            score < 45 -> Color(0xFFFF9500)    // Fear
            score < 55 -> Color(0xFFFFCC00)    // Neutral
            score < 75 -> Color(0xFF32D74B)    // Greed
            else -> Color(0xFF30D158)          // Extreme Greed
        }
    }

    private fun getPCColor(ratio: Double): Color {
        return when {
            ratio < 0.62 -> Color.Gray  // 초낙관 (주의)
            ratio < 0.76 -> Color(0xFF32D74B)   // 낙관적 (초록)
            ratio < 0.94 -> Color(0xFFFFCC00)   // 중립 (노랑)
            ratio < 1.01 -> Color(0xFFFF9500)   // 비관적 (빨강)
            else -> Color.Gray          // 극단적 (주의)
        }
    }
}
