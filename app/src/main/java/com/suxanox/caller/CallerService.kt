package com.suxanox.caller

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class CallerService : Service() {
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var baseUrl = ""
    private var apiKey = ""
    private var isRunning = false
    
    private var isCalling = false 
    private var callRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ambil URL & API Key dari intent (dikirim dari MainActivity)
        baseUrl = intent?.getStringExtra("BASE_URL") ?: return START_NOT_STICKY
        apiKey = intent?.getStringExtra("API_KEY") ?: return START_NOT_STICKY
        
        // Notifikasi biar service gak di-kill Android
        val notification = NotificationCompat.Builder(this, "CALLER_CHANNEL")
            .setContentTitle("Suxanox Caller Aktif")
            .setContentText("Standby menerima perintah...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
        
        startForeground(1, notification)
        
        isRunning = true
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (!isRunning) return
        
        val request = Request.Builder()
            .url("$baseUrl/get-command")
            .addHeader("x-api-key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Kalau gagal konek, coba lagi 5 detik kemudian
                handler.postDelayed({ startPolling() }, 5000)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    try {
                        val json = JSONObject(body)
                        if (json.getString("status") == "ok") {
                            val data = json.getJSONObject("data")
                            val action = data.optString("action", "call")
                            
                            if (action == "stop") {
                                // 🔥 HENTIKAN CALL
                                stopCallLoop()
                                sendReport("0", "Dihentikan Paksa", 0, 0)
                            } else {
                                val target = data.getString("target")
                                val count = data.getInt("count")
                                startCallLoop(target, count)
                            }
                        }
                    } catch (e: Exception) {
                        // Abaikan error parsing
                    }
                }
                // Polling tiap 3 detik
                handler.postDelayed({ startPolling() }, 3000)
            }
        })
    }

    private fun stopCallLoop() {
        isCalling = false
        callRunnable?.let { handler.removeCallbacks(it) }
        
        // Tutup telepon yang lagi jalan
        val endCall = Intent(Intent.ACTION_MEDIA_BUTTON)
        endCall.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
        sendOrderedBroadcast(endCall, null)
    }

    // 🔥 FUNGSI LAPOR PROGRES KE SERVER (DASHBOARD)
    private fun sendReport(target: String, status: String, current: Int, max: Int) {
        val json = JSONObject()
        json.put("target", target)
        json.put("status", status)
        json.put("current", current)
        json.put("max", max)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url("$baseUrl/report")
            .addHeader("x-api-key", apiKey)
            .post(body)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun startCallLoop(target: String, maxCount: Int) {
        // Matiin loop lama kalau ada
        stopCallLoop()
        
        isCalling = true
        var current = 0
        
        callRunnable = object : Runnable {
            override fun run() {
                // Cek apakah masih boleh nelfon
                if (!isCalling || current >= maxCount) {
                    sendReport(target, "Selesai", current, maxCount)
                    return
                }
                current++
                
                // Eksekusi Telepon
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$target")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (ContextCompat.checkSelfPermission(this@CallerService, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent)
                }

                // 🔥 Lapor ke server kalau lagi menelepon
                sendReport(target, "Menelepon...", current, maxCount)

                // Tutup telepon setelah 10 detik, lalu lanjut loop berikutnya
                handler.postDelayed({
                    if (!isCalling) return@postDelayed
                    
                    val endCall = Intent(Intent.ACTION_MEDIA_BUTTON)
                    endCall.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
                    sendOrderedBroadcast(endCall, null)
                    
                    handler.postDelayed(this, 5000) // Jeda 5 detik sebelum nelfon lagi
                }, 10000)
            }
        }
        handler.post(callRunnable!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CALLER_CHANNEL", "Caller Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopCallLoop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}