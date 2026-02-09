package com.fortq.wittq

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit

class AGTQUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 위젯의 provideGlance를 다시 실행시켜서 데이터를 새로 가져옵니다.
            AGTQWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            Result.retry() // 실패 시 네트워크 상황 등에 따라 재시도
        }
    }

    companion object {
        private const val WORK_NAME = "agtq_update_work"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 인터넷 연결 시에만 작동
                .build()

            val request = PeriodicWorkRequestBuilder<AGTQUpdateWorker>(
                60, TimeUnit.MINUTES // 시스템 최소 주기인 15분 설정
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 이미 작동 중이면 새로 만들지 않고 유지
                request
            )
        }
    }
}