package com.stoneparks.btprint

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebhookWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val payload = JSONObject().apply {
            put("id", inputData.getString("id"))
            put("type", inputData.getString("type"))
            put("plate", inputData.getString("plate"))
            put("make", inputData.getString("make"))
            put("model", inputData.getString("model"))
            put("details", inputData.getString("details"))
            put("createdAt", inputData.getString("createdAt"))
            put("source", "android-app")
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success() else Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
