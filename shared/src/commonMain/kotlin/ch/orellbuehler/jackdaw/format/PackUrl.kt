package ch.orellbuehler.jackdaw.format

internal object PackUrl {
    private const val SCHEME = "https://"
    private const val MAX_LENGTH = 2000
    private val host = Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$")

    fun resolve(input: String): String? {
        var url = input.trim()
        if (url.isEmpty() || url.length > MAX_LENGTH || url.any { it.isWhitespace() || it < ' ' }) return null
        if (!url.contains("://")) url = SCHEME + url
        if (!url.startsWith(SCHEME, ignoreCase = true)) return null
        val rest = url.substring(SCHEME.length)
        val hostName =
            rest
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .lowercase()
        if (!host.matches(hostName) || !hostName.substringBefore(':').contains('.')) return null
        val tail = rest.substring(hostName.length)
        val path = tail.substringBefore('#').substringBefore('?')
        val parts = path.split('/').filter { it.isNotEmpty() }
        return when {
            hostName == "gist.github.com" && parts.size == 2 ->
                "${SCHEME}gist.githubusercontent.com/${parts[0]}/${parts[1]}/raw"
            hostName == "github.com" && parts.size > 4 && parts[2] == "blob" ->
                "${SCHEME}raw.githubusercontent.com/${parts[0]}/${parts[1]}/" + parts.drop(3).joinToString("/")
            else -> SCHEME + hostName + tail.substringBefore('#')
        }
    }
}
