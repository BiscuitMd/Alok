package com.suxanox.caller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    
    // 🔥 SINKRON DENGAN XML: layoutDashboard bertipe ScrollView
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: ScrollView 
    
    private var baseUrl = ""
    private var apiKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)

            // Minta izin Telepon (Wajib buat Caller)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }

            // Inisialisasi Layout
            layoutLogin = findViewById(R.id.layoutLogin)
            layoutDashboard = findViewById(R.id.layoutDashboard)

            val editUrl = findViewById<EditText>(R.id.editUrl)
            val editKey = findViewById<EditText>(R.id.editKey)
            val btnLogin = findViewById<Button>(R.id.btnLogin)

            // Logika Tombol Masuk
            btnLogin.setOnClickListener {
                baseUrl = editUrl.text.toString().trim()
                apiKey = editKey.text.toString().trim()
                
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    // Sembunyikan Login, Tampilkan Dashboard
                    layoutLogin.visibility = View.GONE
                    layoutDashboard.visibility = View.VISIBLE
                    
                    // Jalankan Service Background (Mesin Caller)
                    val intent = Intent(this, CallerService::class.java)
                    intent.putExtra("BASE_URL", baseUrl)
                    intent.putExtra("API_KEY", apiKey)
                    ContextCompat.startForegroundService(this, intent)
                    
                    // Mulai Tarik Data dari Bot (Tiap 3 Detik)
                    startDashboardUpdate()
                } else {
                    Toast.makeText(this, "URL dan API Key tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            showError(e)
        }
    }

    // ─── LOOPING UPDATE DASHBOARD ───
    private fun startDashboardUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    fetchStats()
                } catch (e: Exception) {
                    // Abaikan error sesaat biar gak crash
                }
                handler.postDelayed(this, 3000) // 3000ms = 3 detik
            }
        }, 1000)
    }

    // ─── TARIK DATA DARI BOT PTERODACTYL ───
    private fun fetchStats() {
        val request = Request.Builder()
            .url("$baseUrl/bot-stats")
            .addHeader("x-api-key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    try { findViewById<TextView>(R.id.tvCallStatus).text = "⚠️ Gagal konek ke server" } catch (_: Exception) {}
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    try {
                        val json = JSONObject(body)
                        
                        // Ambil data dari JSON Bot
                        val totalUsers = json.optInt("totalUsers", 0)
                        val totalPrem = json.optInt("totalPrem", 0)
                        val progress = json.getJSONObject("progress")
                        
                        val current = progress.optInt("current", 0)
                        val max = progress.optInt("max", 0)
                        val target = progress.optString("target", "-")
                        val status = progress.optString("status", "Idle")

                        // Update UI di Main Thread
                        runOnUiThread {
                            try {
                                findViewById<TextView>(R.id.tvUsers).text = "Users: $totalUsers"
                                findViewById<TextView>(R.id.tvPremium).text = "Premium: $totalPrem"
                                findViewById<TextView>(R.id.tvUserList).text = "Total User: $totalUsers\nPremium/Owner: $totalPrem\n\n(Sinkronisasi Data Bot)"
                                
                                val pb = findViewById<ProgressBar>(R.id.progressCall)
                                pb.max = if (max > 0) max else 100
                                pb.progress = current
                                
                                findViewById<TextView>(R.id.tvCallStatus).text = "Target: $target | Status: $status ($current/$max)"
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            }
        })
    }

    // ─── FUNGSI ANTI-CRASH (Nampilin Error di Layar) ───
    private fun showError(e: Exception) {
        val tvError = TextView(this)
        tvError.text = "CRASH DI APLIKASI:\n\n${e.message}\n\n${e.stackTraceToString()}"
        tvError.setTextColor(android.graphics.Color.RED)
        tvError.textSize = 12f
        tvError.setPadding(30, 100, 30, 30)
        tvError.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(tvError)
    }
}