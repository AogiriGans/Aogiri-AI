package com.xrey7.aogiri.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnMic: Button
    private lateinit var tvStatus: TextView
    private lateinit var adapter: ChatAdapter
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val apiKey = "coa_e683557b9f8ad48b404eae42dcc25362"
    private val apiUrl = "https://coarena.ai/api/v1/chat/completions"
    private val modelName = "gemini-3-8-flash"

    private val prefs by lazy {
        getSharedPreferences("aogiri_ai_prefs", Context.MODE_PRIVATE)
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                handleInput(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                200
            )
        }

        tts = TextToSpeech(this, this)

        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        tvStatus = findViewById(R.id.tvStatus)

        val savedColor = prefs.getInt("accent_color", Color.parseColor("#3B82F6"))
        adapter = ChatAdapter(mutableListOf(), savedColor)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = adapter

        applyAccentColor(savedColor)

        findViewById<Button>(R.id.btnRed).setOnClickListener {
            applyAccentColor(Color.parseColor("#EF4444"))
        }
        findViewById<Button>(R.id.btnPurple).setOnClickListener {
            applyAccentColor(Color.parseColor("#A855F7"))
        }
        findViewById<Button>(R.id.btnBlue).setOnClickListener {
            applyAccentColor(Color.parseColor("#3B82F6"))
        }
        findViewById<Button>(R.id.btnGreen).setOnClickListener {
            applyAccentColor(Color.parseColor("#22C55E"))
        }

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etInput.text.clear()
                handleInput(text)
            }
        }

        btnMic.setOnClickListener {
            startVoiceRecognition()
        }

        appendAiMessage(
            "Aogiri AI aktif. Coba ucapkan 'AI buka YouTube', 'AI tutup YouTube', 'AI Home', atau 'AI Apa itu polisi?'."
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale("id", "ID"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            ttsReady = true
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aogiri_tts")
        }
    }

    private fun applyAccentColor(color: Int) {
        prefs.edit().putInt("accent_color", color).apply()
        btnSend.setBackgroundColor(color)
        btnMic.setBackgroundColor(color)
        adapter.updateAccentColor(color)
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara ke Aogiri AI...")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            appendAiMessage("Layanan pengenalan suara tidak tersedia di perangkat ini.")
        }
    }

    private fun handleInput(rawText: String) {
        adapter.addMessage(ChatItem("Kamu", rawText, true))
        rvChat.scrollToPosition(adapter.itemCount - 1)

        val normalized = rawText.trim().lowercase(Locale.ROOT)
        val command = if (normalized.startsWith("ai ")) {
            normalized.removePrefix("ai ").trim()
        } else {
            normalized
        }

        when {
            command == "buka youtube" -> {
                val reply = "Membuka YouTube."
                appendAiMessage(reply)
                openYouTubeApp()
                return
            }
            command == "tutup youtube" || command == "home" -> {
                val reply = if (command == "tutup youtube") {
                    "Kembali ke Home untuk menutup tampilan YouTube."
                } else {
                    "Kembali ke halaman Home."
                }
                appendAiMessage(reply)
                goToHomeScreen()
                return
            }
        }

        val promptForAi = if (normalized.startsWith("ai ")) {
            rawText.substring(3).trim().ifEmpty { rawText }
        } else {
            rawText
        }

        askAogiriApi(promptForAi)
    }

    private fun openYouTubeApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun askAogiriApi(userPrompt: String) {
        tvStatus.text = "Aogiri AI sedang berpikir..."
        Thread {
            try {
                val url = URL(apiUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 20000
                    readTimeout = 25000
                }

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Kamu adalah Aogiri AI, asisten suara dan obrolan santai berbahasa Indonesia yang jelas, ramah, dan ringkas.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }

                val payload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", messagesArray)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val rawResponse = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                conn.disconnect()

                val replyText = if (code in 200..299) {
                    val json = JSONObject(rawResponse)
                    val choices = json.optJSONArray("choices")
                    val firstMsg = choices?.optJSONObject(0)?.optJSONObject("message")
                    firstMsg?.optString("content")?.trim().orEmpty().ifEmpty {
                        "Respons kosong diterima dari server."
                    }
                } else {
                    "Gagal menghubungi API (HTTP $code)."
                }

                runOnUiThread {
                    tvStatus.text = "Siap"
                    appendAiMessage(replyText)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Siap"
                    appendAiMessage("Terjadi kesalahan koneksi: ${e.localizedMessage ?: "error"}")
                }
            }
        }.start()
    }

    private fun appendAiMessage(text: String) {
        adapter.addMessage(ChatItem("Aogiri AI", text, false))
        rvChat.scrollToPosition(adapter.itemCount - 1)
        speak(text)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
