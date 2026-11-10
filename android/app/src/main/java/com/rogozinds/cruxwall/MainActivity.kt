package com.rogozinds.cruxwall

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

/**
 * Hosts the Crux Wall game (bundled from /web) in a full-screen WebView.
 * Pose tracking runs on-device inside the page with MediaPipe; this activity
 * only grants camera access, keeps the screen awake and speaks coach lines.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingCameraRequest: PermissionRequest? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingCameraRequest ?: return@registerForActivityResult
            pendingCameraRequest = null
            if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // Serves app assets from a secure https origin, which getUserMedia and ES modules require.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", TypedAssetsHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(0xFF1D1B19.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { handleWebPermission(request) }
                }
            }

            addJavascriptInterface(Bridge(), "AndroidBridge")
        }

        setContentView(webView)
        webView.loadUrl(START_URL)
    }

    private fun handleWebPermission(request: PermissionRequest) {
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE !in request.resources) {
            request.deny()
            return
        }
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        } else {
            pendingCameraRequest?.deny()
            pendingCameraRequest = request
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    /** Exposed to the page as window.AndroidBridge. */
    inner class Bridge {
        @JavascriptInterface
        fun speak(text: String) {
            if (ttsReady) tts?.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, null, "coach")
        }

        @JavascriptInterface
        fun stopSpeaking() {
            tts?.stop()
        }
    }

    companion object {
        private const val START_URL = "https://appassets.androidplatform.net/assets/web/index.html"
    }
}
