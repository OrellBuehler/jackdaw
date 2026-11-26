package ch.orellbuehler.jackdaw.ui.study

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
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
import ch.orellbuehler.jackdaw.StoreScreenshots
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
                android.util.Log.i("FigureDebug", "load ${html.length} chars: ${html.take(600)}")
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
        if (StoreScreenshots.active) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = true

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    android.util.Log.i("FigureDebug", "started $url ${view.width}x${view.height} attached=${view.isAttachedToWindow} shown=${view.isShown}")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    android.util.Log.i("FigureDebug", "finished $url ${view.width}x${view.height} content=${view.contentHeight} progress=${view.progress} shown=${view.isShown} alpha=${view.alpha}")
                    view.postDelayed({
                        android.util.Log.i("FigureDebug", "later ${view.width}x${view.height} content=${view.contentHeight} shown=${view.isShown} layer=${view.layerType} hw=${view.isHardwareAccelerated}")
                        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                        view.draw(android.graphics.Canvas(bitmap))
                        val probes = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).map { Integer.toHexString(bitmap.getPixel((view.width * it).toInt(), view.height / 2)) }
                        var p: android.view.ViewParent? = view.parent
                        val chain = StringBuilder()
                        while (p != null) { chain.append(p.javaClass.simpleName).append(if (p is View) "(a=${p.alpha},v=${p.visibility},${p.width}x${p.height},layer=${p.layerType})" else "").append(" < "); p = p.parent }
                        android.util.Log.i("FigureDebug", "probe $probes scroll=${view.scrollX},${view.scrollY} scale=${view.scaleX} parents=$chain")
                        figureDebugVariants(view, view.tag as? String ?: "")
                    }, 3000)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    android.util.Log.i("FigureDebug", "error ${request.url} ${error.errorCode} ${error.description}")
                }

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    android.util.Log.i("FigureDebug", "render process gone crash=${detail.didCrash()}")
                    return true
                }
            }
    }

private var figureDebugRan = false

private fun figureDebugVariants(
    view: WebView,
    html: String,
) {
    if (figureDebugRan) return
    figureDebugRan = true
    val csp = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">"
    val variants =
        listOf(
            "red" to "<!DOCTYPE html><html><body style=\"background:red\">x</body></html>",
            "nocsp" to html.replace(csp, ""),
            "cspred" to "<!DOCTYPE html><html><head>$csp<style>body{background:red}</style></head><body>x</body></html>",
            "opaque" to html.replace("background:transparent", "background:#00ff00"),
            "noviewport" to html.replace(Regex("<meta name=\"viewport\"[^>]*>"), ""),
            "original" to html,
        )
    fun probe(name: String) {
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val probes = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).map { Integer.toHexString(bitmap.getPixel((view.width * it).toInt(), view.height / 2)) }
        android.util.Log.i("FigureDebug", "variant $name $probes content=${view.contentHeight}")
    }
    fun step(index: Int) {
        if (index >= variants.size) return
        view.tag = "debug"
        view.loadDataWithBaseURL(null, variants[index].second, "text/html", "utf-8", null)
        view.postDelayed({
            probe(variants[index].first)
            step(index + 1)
        }, 2500)
    }
    step(0)
}
