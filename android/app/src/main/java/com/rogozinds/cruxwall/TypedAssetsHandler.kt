package com.rogozinds.cruxwall

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader

/**
 * Serves bundled assets with correct MIME types. Module scripts (.mjs) must be
 * served as JavaScript and WebAssembly as application/wasm, which Android's
 * default guess does not do.
 */
class TypedAssetsHandler(context: Context) : WebViewAssetLoader.PathHandler {
    private val inner = WebViewAssetLoader.AssetsPathHandler(context)

    override fun handle(path: String): WebResourceResponse? {
        val response = inner.handle(path) ?: return null
        mimeTypeFor(path)?.let { response.mimeType = it }
        return response
    }

    private fun mimeTypeFor(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "wasm" -> "application/wasm"
        "css" -> "text/css"
        "json" -> "application/json"
        "task" -> "application/octet-stream"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> null
    }
}
