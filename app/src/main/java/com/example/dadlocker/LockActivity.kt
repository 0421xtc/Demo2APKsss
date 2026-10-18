package com.example.dadlocker

import android.app.KeyguardManager
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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class LockActivity : AppCompatActivity() {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tvHint: TextView
    private lateinit var tvMock: TextView
    private lateinit var btnMic: TextView

    private var stage = 1
    private var listening = false
    private var intentionalExit = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val exitRunnable = Runnable { exitPrank() }

    companion object {
        private const val HINT_1 = "🚨 警告：设备已被安全协议锁定 🚨\n\n请点击下方麦克风，进行声纹验证：\n对该手机大喊「爸爸」"
        private const val HINT_2 = "⚠️ 二次验证 ⚠️\n\n请继续大喊：「爸爸对不起」"

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

        tvHint.text = HINT_1

        findViewById<FrameLayout>(R.id.root).setOnClickListener {
            if (stage < 3) mock("系统提示：请快点配合语音验证，否则将永久锁定数据！\n😏")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mock("警告：强制退出将被记录，请完成语音验证！\n😏")
            }
        })

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvHint.text = "⚠️ 系统组件缺失\n请重启手机或检查网络…"
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(listener)

        btnMic.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(exitRunnable, 8000)
                    startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(exitRunnable)
                    true
                }
                else -> false
            }
        }
        
        handler.postDelayed({
            mock("请点击下方麦克风图标进行验证！")
        }, 1000)
    }
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { tvHint.text = "🎤 正在监听…请大喊！" }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        
        override fun onError(error: Int) {
            listening = false
            handler.postDelayed({
                if (stage < 3 && !intentionalExit) {
                    tvHint.text = if (stage == 1) HINT_1 else HINT_2
                    startListening()
                }
            }, 1000)
        }
        
        override fun onResults(results: Bundle?) {
            listening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                handleSpeech(matches)
            } else {
                startListening()
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startListening() {
        if (listening) return
        listening = true
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    private fun handleSpeech(matches: List<String>) {
        val saidDad = matches.any { isDad(it) }
        val saidSorry = matches.any { it.contains("对不起") || it.contains("抱歉") }
        
        when (stage) {
            1 -> if (saidDad) {
                stage = 2
                vibrate()
                tvHint.text = HINT_2
                mock("验证通过第一层！")
                handler.postDelayed({ startListening() }, 1000)
            } else {
                mock("❌ 验证失败！请老实喊『爸爸』！\n😏")
                startListening()
            }
            
            2 -> when {
                saidDad && saidSorry -> unlock()
                saidSorry -> {
                    mock("❌ 缺少尊称！要连起来说『爸爸对不起』！\n😏")
                    startListening()
                }
                saidDad -> {
                    mock("❌ 不够诚心！请说『爸爸对不起』！\n😏")
                    startListening()
                }
                else -> {
                    mock("❌ 系统拒绝！请说『爸爸对不起』！\n😏")
                    startListening()
                }
            }
        }
    }

    private fun isDad(text: String): Boolean {
        val keywords = listOf("爸爸", "粑粑", "把把", "巴巴", "哒哒", "爹")
        return keywords.any { text.contains(it) }
    }
    
    private fun mock(msg: String) {
        tvMock.text = msg
        tvMock.visibility = View.VISIBLE
        vibrate()
        handler.postDelayed({ tvMock.visibility = View.GONE }, 3000)
    }

    private fun unlock() {
        stage = 3
        vibrate()
        tvMock.visibility = View.GONE
        tvHint.text = "✅ 声纹验证通过，系统已解锁！"
        handler.postDelayed({ exitPrank() }, 1500)
    }

    private fun exitPrank() {
        intentionalExit = true
        Prefs.setActive(this, false)
        finish()
    }

    private fun vibrate() {
        val vb = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vb.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vb.vibrate(100)
        }
    }

    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        if (::recognizer.isInitialized) recognizer.destroy()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        if (!intentionalExit && stage < 3 && Prefs.isActive(this)) {
            LockActivity.start(this)
        }
    }
}
