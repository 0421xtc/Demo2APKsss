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

    private var stage = 1
    private var listening = false
    private var intentionalExit = false
    private val handler = Handler(Looper.getMainLooper())
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

        findViewById<FrameLayout>(R.id.root).setOnClickListener {
            if (stage < 3) mock("叫爸爸就可以啦～\n😏")
        }
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

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

    private fun isDad(text: String): Boolean {
        val keywords = listOf("爸爸", "粑粑", "把把", "巴巴", "哒哒", "爹")
        return keywords.any { text.contains(it) }
    }
    
    private fun mock(msg: String) {
        tvMock.text = msg
        tvMock.visibility = View.VISIBLE
        tvMock.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
        vibrate()
    }

    private fun unlock() {
        stage = 3
        vibrate()
        tvMock.visibility = View.GONE
        tvHint.text = "真乖～😄 解锁啦！"
        handler.postDelayed({ exitPrank() }, 1600)
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
            vb.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vb.vibrate(80)
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

