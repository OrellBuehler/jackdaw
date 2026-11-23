package ch.orellbuehler.jackdaw.ui.study

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.Figure
import ch.orellbuehler.jackdaw.ui.theme.cardSurface

val LocalFigure = compositionLocalOf<Figure?> { null }

fun cssColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

@Composable
fun FigureCard(maxHeight: Dp = 240.dp) {
    val figure = LocalFigure.current ?: return
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val html = remember(figure, textColor) { figure.html(cssColor(textColor)) }
    val description = figure.alt ?: stringResource(R.string.figure_cd)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.cardSurface)
            .padding(12.dp)
            .semantics {
                contentDescription = description
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.heightIn(max = maxHeight).aspectRatio(figure.aspectRatio.toFloat().coerceIn(0.2f, 5f))) {
            AndroidView(
                factory = { context -> passiveWebView(context) },
                update = { view ->
                    if (view.tag != html) {
                        view.tag = html
                        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().pointerInput(Unit) {})
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun passiveWebView(context: android.content.Context): WebView =
    WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = false
        settings.blockNetworkLoads = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isFocusable = false
        isLongClickable = false
        importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true
            }
    }
