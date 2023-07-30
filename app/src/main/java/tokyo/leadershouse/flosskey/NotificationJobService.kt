package tokyo.leadershouse.flosskey
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

@SuppressLint("SpecifyJobSchedulerIdRange")
class NotificationJobService : JobService() {
    private var notificationId = 0 // 通知IDを保持する変数
    companion object {
        private const val NOTIFICATION_CHANNEL_ID   = "flosskey_notifications"
        private const val NOTIFICATION_CHANNEL_NAME = "Flosskey Notifications"
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("debug","onStartJob[IN]")
        val instanceName = params?.extras?.getString("instanceName")
        val apiKey       = params?.extras?.getString("apiKey")
        val jobId        = params?.extras?.getInt("jobId")
        if (apiKey != null) { fetchNotifications(apiKey, instanceName!!) }
        else { return false }
        scheduleJob(jobId!!)
        Log.d("debug","onStartJob[OUT]")
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("debug","onStopJob[IN]")
        Log.d("debug","onStopJob[OUT]")
        return true
    }

    private fun fetchNotifications(apiKey: String, instanceName: String) {
        Log.d("debug","fetchNotifications[IN]")
        val thread = Thread {
            val client = OkHttpClient()
            // ぶっちゃけsinceIdを変換してハンドリングしたいが一旦はcreatedAtで通知判定する...
            val requestBody = JSONObject()
                .put("i", apiKey)
                .put("limit", 100)
                .toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(getMisskeyUrlData("API",instanceName))
                .post(requestBody)
                .build()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrBlank()) { processNotifications(responseBody) }
                } else { Log.d("debug", "Failed to retrieve notifications: ${response.code}") }
            } catch (e: Exception) { Log.d("debug", "Failed to retrieve notifications", e) }
        }
        thread.start()
        Log.d("debug","fetchNotifications[OUT]")
    }

    private fun processNotifications(responseBody: String) {
        Log.d("debug", "processNotifications[IN]")
        val jsonArray = JSONArray(responseBody)
        val keyStore  = KeyStoreHelper.getKeyStore(this)
        // 端末が既知の最新の通知のcratedAt
        val instantDevice = Instant.parse(
            keyStore.getString(
                "createdAt",
                "2000-01-01T00:00:00.000Z"
            ) ?: "")
        for (i in 0 until jsonArray.length()) {
            val notification = jsonArray.optJSONObject(i)
            val createdAt = notification.optString("createdAt")
            // API叩いて取得した通知のcratedAt
            val instantApi = Instant.parse(createdAt)
            val comparisonResult = instantApi.compareTo(instantDevice)
            if (comparisonResult > 0) {
                val type = notification.optString("type")
                val user = notification.optJSONObject("user")
                val name = user?.optString("name")
                val reaction = notification.optString("reaction")
                val message = when (type) {
                    "follow"               -> "${name}にフォローされました"
                    "mention"              -> "${name}にメンションされました"
                    "reply"                -> "${name}にリノートされました"
                    "quote"                -> "${name}に引用されました"
                    "reaction"             -> "${name}から${reaction}されました"
                    "receiveFollowRequest" -> "${name}からフォロー申請されました"
                    "allowFollowRequest"   -> "${name}へのフォローが許可されました"
                    else -> continue // 今度対応
                }
                if (message.isNotEmpty()) { sendNotification(message) }
            }
            else { break }
        }
        val tempolaryId = jsonArray.optJSONObject(0).optString("createdAt")
        val editor = keyStore.edit()
        editor.putString("createdAt", tempolaryId)
        editor.apply()
        Log.d("debug", "processNotifications[OUT]")
    }

    private fun sendNotification(message: String) {
        Log.d("debug","sendNotification[IN]")
        val channelId = NOTIFICATION_CHANNEL_ID
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // アプリを開くためのIntentを作成
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(NOTIFICATION_CHANNEL_NAME)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent) // PendingIntentを設定
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
        notificationManager.notify(notificationId++, notificationBuilder.build())
        Log.d("debug","sendNotification[OUT]")
    }

    private fun scheduleJob(jobId: Int) {
        Log.d("debug","scheduleJob[IN]")
        val componentName = ComponentName(this, NotificationJobService::class.java)
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobInfo = JobInfo.Builder(jobId, componentName)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(0)
            .build()
        jobScheduler.schedule(jobInfo)
        Log.d("debug","scheduled: $jobId")
        Log.d("debug","scheduleJob[OUT]")
    }
}
