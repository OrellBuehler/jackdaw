package ch.orellbuehler.jackdaw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import ch.orellbuehler.jackdaw.format.TokenKind

@Immutable
class CodeColors(
    val surface: Color,
    val border: Color,
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
) {
    fun of(kind: TokenKind): Color =
        when (kind) {
            TokenKind.PLAIN -> plain
            TokenKind.KEYWORD -> keyword
            TokenKind.STRING -> string
            TokenKind.COMMENT -> comment
            TokenKind.NUMBER -> number
            TokenKind.TYPE -> type
        }
}

// every token colour has a contrast of at least 5.5 : 1 against its surface
val LightCodeColors =
    CodeColors(
        surface = Color(0xFFEDEFF7),
        border = Color(0xFFD3D7E6),
        plain = Color(0xFF1F2330),
        keyword = Color(0xFFA8205F),
        string = Color(0xFF0B6B4B),
        comment = Color(0xFF586071),
        number = Color(0xFF0A57A6),
        type = Color(0xFF7436B0),
    )

val DarkCodeColors =
    CodeColors(
        surface = Color(0xFF0C0B14),
        border = Color(0xFF332F45),
        plain = Color(0xFFE6E8F0),
        keyword = Color(0xFFFF8FB8),
        string = Color(0xFF7EE2B0),
        comment = Color(0xFF9AA1B2),
        number = Color(0xFF82B8FF),
        type = Color(0xFFC7A6FF),
    )

val MaterialTheme.code: CodeColors
    @Composable
    @ReadOnlyComposable
    get() = if (LocalDarkTheme.current) DarkCodeColors else LightCodeColors
