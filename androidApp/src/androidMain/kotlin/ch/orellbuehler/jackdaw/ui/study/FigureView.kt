package ch.orellbuehler.jackdaw.ui.study

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.orellbuehler.jackdaw.R
import ch.orellbuehler.jackdaw.format.Figure
import ch.orellbuehler.jackdaw.ui.theme.cardSurface

val LocalFigure = compositionLocalOf<Figure?> { null }

fun cssColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private val MAP_MAX_HEIGHT = 320.dp

@Composable
fun FigureCard(maxHeight: Dp = 240.dp) {
    val figure = LocalFigure.current ?: return
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val html = remember(figure, textColor) { figure.html(cssColor(textColor)) }
    val description = figure.alt ?: stringResource(if (figure.isMap) R.string.map_cd else R.string.figure_cd)
    val openLabel = stringResource(if (figure.isMap) R.string.map_open else R.string.figure_open)
    val ratio = figure.aspectRatio.toFloat().coerceIn(0.2f, 5f)
    var zoomed by remember(html) { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val semantics =
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            role = Role.Image
        }
    if (figure.isMap) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .heightIn(max = maxOf(maxHeight, MAP_MAX_HEIGHT))
                    .aspectRatio(ratio)
                    .clip(shape)
                    .background(MaterialTheme.cardSurface)
                    .then(semantics),
            ) {
                PassiveFigure(html, openLabel) { zoomed = true }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.cardSurface)
                .padding(12.dp)
                .then(semantics),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.heightIn(max = maxHeight).aspectRatio(ratio)) {
                PassiveFigure(html, openLabel) { zoomed = true }
            }
        }
    }
    if (zoomed) FigureDialog(html, description) { zoomed = false }
}

@Composable
private fun PassiveFigure(
    html: String,
    openLabel: String,
    onOpen: () -> Unit,
) {
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
    Box(Modifier.fillMaxSize().clickable(onClickLabel = openLabel, onClick = onOpen))
}

@Composable
private fun FigureDialog(
    html: String,
    description: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context -> zoomableWebView(context) },
                    update = { view ->
                        if (view.tag != html) {
                            view.tag = html
                            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.figure_close))
                }
            }
        }
    }
}

private fun zoomableWebView(context: android.content.Context): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = false
        settings.blockNetworkLoads = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true
            }
    }

@SuppressLint("ClickableViewAccessibility")
private fun passiveWebView(context: android.content.Context): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
