package com.example.dadlocker

import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tvHint: TextView
    private lateinit var tvMock: TextView
    private lateinit var btnMic: TextView

    private var stage = 1               // 1=等"爸爸" 2=等"爸爸对不起" 3=已解锁
    private var listening = false
    private var intentionalExit = false
    private val handler = Handler(Looper.getMainLooper())

    // 隐藏逃生门：按住语音键8秒，整蛊彻底结束（只告诉你）
    private val exitRunnable = Runnable { exitPrank() }

    companion object {
        private const val HINT_1 = "手机已被锁定 🔒\n想解锁？对着手机喊：爸爸"
        private const val HINT_2 = "嗯？光叫爸爸就想走？\n再说一句：爸爸对不起"

        fun start(context: Context) {
            context.startActivity(Intent(context, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        setContentView(R.layout.activity_lock)

        tvHint = findViewById(R.id.tvHint)
        tvMock = findViewById(R.id.tvMock)
        btnMic = findViewById(R.id.btnMic)

        // 点屏幕空白处 = 嘲笑
        findViewById<FrameLayout>(R.id.root).setOnClickListener {
            if (stage < 3) mock("叫爸爸就可以啦～\n😏")
        }

        // 返回键（含手势返回）没用的
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mock("返回键没用的～叫爸爸\n😏")
            }
        })

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvHint.text = "此手机不支持语音识别…"
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        // 按住说话，松开识别
        btnMic.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(exitRunnable, 8000)
                    startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(exitRunnable)
                    recognizer.stopListening()
                    true
                }
                else -> false
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { tvHint.text = "听着呢，说吧…" }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            listening = false
            tvHint.text = if (stage == 1) HINT_1 else HINT_2
        }
        override fun onResults(results: Bundle?) {
            listening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) handleSpeech(matches)
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (listening) return
        listening = true
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    // AI识别：模糊匹配，音近也算（粑粑/把把/巴巴都算"爸爸"）
    private fun handleSpeech(matches: List<String>) {
        val saidDad = matches.any { isDad(it) }
        val saidSorry = matches.any { it.contains("对不起") }
        when (stage) {
            1 -> if (saidDad) {
                stage = 2
                vibrate()
                tvHint.text = HINT_2
            } else mock("不对不对，要喊『爸爸』才行～\n😏")

            2 -> when {
                saidDad && saidSorry -> unlock()
                saidSorry -> mock("还差『爸爸』两个字哦～\n😏")
                saidDad -> mock("光叫爸爸可不够，要说『爸爸对不起』～\n😏")
                else -> mock("要说『爸爸对不起』才行～\n😏")
            }
        }
    }

    private f
