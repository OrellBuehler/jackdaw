package ch.orellbuehler.jackdaw.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

fun interface TextFetcher {
    suspend fun fetch(url: String): String?
}

class HttpsTextFetcher : TextFetcher {
    override suspend fun fetch(url: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as? HttpsURLConnection ?: return@withContext null
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.instanceFollowRedirects = true
                    if (connection.responseCode != HttpsURLConnection.HTTP_OK) return@withContext null
                    (connection.inputStream.use { decodePackText(it) } as? ReadOutcome.Text)?.text
                } finally {
                    connection.disconnect()
                }
            } catch (_: IOException) {
                null
            }
        }
}
