package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {
    private var webViewRef: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uris = when {
                data?.clipData != null -> Array(data.clipData!!.itemCount) { idx ->
                    data.clipData!!.getItemAt(idx).uri
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            callback.onReceiveValue(uris)
        } else {
            callback.onReceiveValue(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewRef
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): WebResourceResponse? {
                                        val uri = request.url
                                        if (uri.host != ASSET_HOST) return null
                                        val rawPath = uri.path?.trimStart('/') ?: ""
                                        val assetPath = if (rawPath.isEmpty()) "www/index.html" else "www/$rawPath"
                                        return try {
                                            val stream = ctx.assets.open(assetPath)
                                            WebResourceResponse(guessMimeType(assetPath), "UTF-8", stream)
                                        } catch (_: Exception) {
                                            if (!rawPath.contains('.')) {
                                                try {
                                                    val fallback = ctx.assets.open("www/index.html")
                                                    WebResourceResponse("text/html", "UTF-8", fallback)
                                                } catch (_: Exception) {
                                                    WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                                }
                                            } else {
                                                WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest
                                    ): Boolean {
                                        val uri = request.url
                                        if (uri.host == ASSET_HOST) return false
                                        return try {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                        webView: WebView,
                                        callback: ValueCallback<Array<Uri>>,
                                        params: FileChooserParams
                                    ): Boolean {
                                        filePathCallback?.onReceiveValue(null)
                                        filePathCallback = callback
                                        return try {
                                            fileChooserLauncher.launch(params.createIntent())
                                            true
                                        } catch (_: Exception) {
                                            filePathCallback = null
                                            false
                                        }
                                    }
                                }
                                loadUrl("https://$ASSET_HOST/index.html")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        webViewRef?.destroy()
        webViewRef = null
        super.onDestroy()
    }

    private fun guessMimeType(path: String): String = when {
        path.endsWith(".html", ignoreCase = true) -> "text/html"
        path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true) -> "application/javascript"
        path.endsWith(".css", ignoreCase = true) -> "text/css"
        path.endsWith(".json", ignoreCase = true) -> "application/json"
        path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".webp", ignoreCase = true) -> "image/webp"
        path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        else -> "application/octet-stream"
    }

    companion object {
        private const val ASSET_HOST = "appassets.androidplatform.net"
    }
}
