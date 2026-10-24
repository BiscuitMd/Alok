package com.suxanox.caller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutDashboard: LinearLayout
    
    private var baseUrl = ""
    private var apiKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutLogin = findViewById(R.id.layoutLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)

        val editUrl = findViewById<EditText>(R.id.editUrl)
        val editKey = findViewById<EditText>(R.id.editKey)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            baseUrl = editUrl.text.toString().trim()
            apiKey = editKey.text.toString().trim()
            
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                // Pindah ke Dashboard
                layoutLogin.visibility = LinearLayout.GONE
                layoutDashboard.visibility = LinearLayout.VISIBLE
                
                // Jalanin Service Background
                val intent = Intent(this, CallerService::class.java)
                intent.putExtra("BASE_URL", baseUrl)
                intent.putExtra("API_KEY", apiKey)
                ContextCompat.startForegroundService(this, intent)
                
                // Mulai update UI tiap 3 detik
                startDashboardUpdate()
            } else {
                Toast.makeText(this, "Isi semua kolom!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDashboardUpdate() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                fetchStats()
                handler.postDelayed(this, 3000) // Update tiap 3 detik
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
                    findViewById<TextView>(R.id.tvCallStatus).text = "Gagal konek ke server"
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
                            findViewById<TextView>(R.id.tvUsers).text = "Users: $totalUsers"
                            findViewById<TextView>(R.id.tvPremium).text = "Premium: $totalPrem"
                            findViewById<TextView>(R.id.tvUserList).text = "Total User: $totalUsers\nPremium/Owner: $totalPrem\n\n(Sinkronisasi Data Bot)"
                            
                            val pb = findViewById<ProgressBar>(R.id.progressCall)
                            pb.max = if (max > 0) max else 100
                            pb.progress = current
                            
                            findViewById<TextView>(R.id.tvCallStatus).text = "Target: $target | Status: $status ($current/$max)"
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }
}