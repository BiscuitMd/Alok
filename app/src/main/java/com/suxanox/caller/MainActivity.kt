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
    
    // 🔥 FIX 1: Ubah tipe data jadi ScrollView
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: ScrollView 
    
    private var baseUrl = ""
    private var apiKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }

            layoutLogin = findViewById(R.id.layoutLogin)
            layoutDashboard = findViewById(R.id.layoutDashboard)

            val editUrl = findViewById<EditText>(R.id.editUrl)
            val editKey = findViewById<EditText>(R.id.editKey)
            val btnLogin = findViewById<Button>(R.id.btnLogin)

            btnLogin.setOnClickListener {
                baseUrl = editUrl.text.toString().trim()
                apiKey = editKey.text.toString().trim()
                
                if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                    layoutLogin.visibility = View.GONE
                    
                    // 🔥 FIX 2: Ubah LinearLayout.VISIBLE jadi View.VISIBLE
                    layoutDashboard.visibility = View.VISIBLE 
                    
                    val intent = Intent(this, CallerService::class.java)
                    intent.putExtra("BASE_URL", baseUrl)
                    intent.putExtra("API_KEY", apiKey)
                    ContextCompat.startForegroundService(this, intent)
                    
                    startDashboardUpdate()
                } else {
                    Toast.makeText(this, "Isi semua kolom!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            val tvError = TextView(this)
            tvError.text = "APLIKASI CRASH:\n\n${e.message}\n\n${e.stackTraceToString()}"
            tvError.setTextColor(android.graphics.Color.RED)
            tvError.textSize = 14f
            tvError.setPadding(30, 100, 30, 30)
            setContentView(tvError)
        }
    }

    private fun startDashboardUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchStats()
                handler.postDelayed(this, 3000)
            }
        }, 1000)
    }

    private fun fetchStats() {
        val request = Request.Builder()
            .url("$baseUrl/bot-stats")
            .addHeader("x-api-key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    try { findViewById<TextView>(R.id.tvCallStatus).text = "Gagal konek ke server" } catch (_: Exception) {}
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    try {
                        val json = JSONObject(body)
                        val totalUsers = json.optInt("totalUsers", 0)
                        val totalPrem = json.optInt("totalPrem", 0)
                        val progress = json.getJSONObject("progress")
                        
                        val current = progress.optInt("current", 0)
                        val max = progress.optInt("max", 0)
                        val target = progress.optString("target", "-")
                        val status = progress.optString("status", "Idle")

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
}