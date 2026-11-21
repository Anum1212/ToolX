package com.toolx.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.toolx.app.tools.ToolCategory

enum class ThemeGroup(val title: String) {
    BASIC("Basic"),
    POPULAR("Popular"),
    LIGHT("Light themes"),
    DARK("Dark themes"),
}

/**
 * A complete app palette. [accents] holds one colour per [ToolCategory], in enum order, so
 * every category keeps its own colour in every theme. Colours are the scheme's own where it
 * has enough hues; gaps are filled with close, harmonious tones.
 */
data class AppTheme(
    val id: String,
    val name: String,
    val group: ThemeGroup,
    val dark: Boolean,
    val background: Color,
    /** Cards and sheets. */
    val surface: Color,
    /** Inputs, chips, raised areas. */
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val primary: Color,
    val accents: List<Color>,
) {
    fun accent(category: ToolCategory): Color = accents[category.ordinal]
}

// ---------- colour maths (pure; unit-tested) ----------

/** WCAG contrast ratio, 1..21. */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** [fg] nudged towards black or white until it reaches [min] contrast on [bg]. */
fun readable(fg: Color, bg: Color, min: Float = 3f): Color {
    if (contrast(fg, bg) >= min) return fg
    val target = if (bg.luminance() > 0.4f) Color.Black else Color.White
    var t = 0f
    var c = fg
    while (contrast(c, bg) < min && t < 1f) {
        t += 0.04f
        c = lerp(fg, target, t)
    }
    return c
}

/** Black-ish or white text, whichever reads better on [c]. */
fun onColor(c: Color): Color = if (contrast(Color(0xFF111111), c) >= contrast(Color.White, c)) Color(0xFF111111) else Color.White

/** A soft tint of [accent] for containers on [base]. */
fun tint(accent: Color, base: Color, dark: Boolean): Color = lerp(base, accent, if (dark) 0.24f else 0.16f)

// ---------- catalogue ----------

private fun c(v: Long) = Color(v)

private fun theme(
    id: String, name: String, group: ThemeGroup, dark: Boolean,
    bg: Long, surface: Long, high: Long, text: Long, muted: Long, outline: Long, primary: Long,
    vararg accents: Long,
) = AppTheme(id, name, group, dark, c(bg), c(surface), c(high), c(text), c(muted), c(outline), c(primary), accents.map(::c))

// Accent order = ToolCategory order:
// VIDEO, AUDIO, IMAGE, PDF, TEXT, UNITS, CALC, GENERAL, FINANCE, RANDOM, DEV

private val MaterialLightAccents = longArrayOf(
    0xFFE53935, 0xFFF57C00, 0xFF43A047, 0xFFD81B60, 0xFF1E88E5, 0xFF00897B,
    0xFF8E24AA, 0xFFF9A825, 0xFF3949AB, 0xFF7CB342, 0xFF546E7A,
)
private val MaterialDarkAccents = longArrayOf(
    0xFFF28B82, 0xFFFFB74D, 0xFF81C995, 0xFFF48FB1, 0xFF8AB4F8, 0xFF80CBC4,
    0xFFCE93D8, 0xFFFFD54F, 0xFF9FA8DA, 0xFFC5E1A5, 0xFFB0BEC5,
)
private val NordAccents = longArrayOf(
    0xFFBF616A, 0xFFD08770, 0xFFA3BE8C, 0xFFB48EAD, 0xFF81A1C1, 0xFF8FBCBB,
    0xFF9D8BC2, 0xFFEBCB8B, 0xFF88C0D0, 0xFF5E81AC, 0xFFD8DEE9,
)
private val SolarizedAccents = longArrayOf(
    0xFFDC322F, 0xFFCB4B16, 0xFF859900, 0xFFD33682, 0xFF268BD2, 0xFF2AA198,
    0xFF6C71C4, 0xFFB58900, 0xFF5FA3E0, 0xFFE06C9F, 0xFF93A1A1,
)

/** System default: follows the phone's light/dark setting. */
const val SYSTEM_THEME = "system"
/** Material You: colours from the wallpaper (Android 12+). */
const val DYNAMIC_THEME = "dynamic"

object Themes {
    val Light = theme("light", "Light", ThemeGroup.BASIC, false,
        0xFFF6F6FA, 0xFFFFFFFF, 0xFFEBEBF2, 0xFF1B1B21, 0xFF5D5D6B, 0xFFC7C7D3, 0xFF4F46E5, *MaterialLightAccents)
    val Dark = theme("dark", "Dark", ThemeGroup.BASIC, true,
        0xFF111115, 0xFF1C1C22, 0xFF27272F, 0xFFE5E5EA, 0xFFA1A1AE, 0xFF3C3C46, 0xFF8B85FF, *MaterialDarkAccents)
    val Amoled = theme("amoled", "AMOLED", ThemeGroup.BASIC, true,
        0xFF000000, 0xFF0E0E10, 0xFF1A1A1E, 0xFFE8E8EC, 0xFF9E9EA8, 0xFF2E2E34, 0xFF8B85FF, *MaterialDarkAccents)

    val all: List<AppTheme> = listOf(
        Light, Dark, Amoled,

        theme("nord", "Nord", ThemeGroup.POPULAR, true,
            0xFF2E3440, 0xFF3B4252, 0xFF434C5E, 0xFFECEFF4, 0xFFAEB7C6, 0xFF4C566A, 0xFF88C0D0, *NordAccents),
        theme("one_dark_pro", "One Dark Pro", ThemeGroup.POPULAR, true,
            0xFF282C34, 0xFF2F343E, 0xFF3E4451, 0xFFD7DAE0, 0xFF8B93A1, 0xFF3E4451, 0xFF61AFEF,
            0xFFE06C75, 0xFFD19A66, 0xFF98C379, 0xFFD55FDE, 0xFF61AFEF, 0xFF56B6C2,
            0xFF8E8CF0, 0xFFE5C07B, 0xFF7FD1A0, 0xFFBE5046, 0xFFABB2BF),

        // ---- 10 light ----
        theme("catppuccin_latte", "Catppuccin Latte", ThemeGroup.LIGHT, false,
            0xFFE6E9EF, 0xFFEFF1F5, 0xFFDCE0E8, 0xFF4C4F69, 0xFF6C6F85, 0xFFBCC0CC, 0xFF8839EF,
            0xFFD20F39, 0xFFFE640B, 0xFF40A02B, 0xFFEA76CB, 0xFF1E66F5, 0xFF179299,
            0xFF8839EF, 0xFFDF8E1D, 0xFF209FB5, 0xFFDD7878, 0xFF7287FD),
        theme("gruvbox_light", "Gruvbox Light", ThemeGroup.LIGHT, false,
            0xFFFBF1C7, 0xFFF9F5D7, 0xFFEBDBB2, 0xFF3C3836, 0xFF7C6F64, 0xFFD5C4A1, 0xFFAF3A03,
            0xFF9D0006, 0xFFAF3A03, 0xFF79740E, 0xFFB16286, 0xFF076678, 0xFF427B58,
            0xFF8F3F71, 0xFFB57614, 0xFF458588, 0xFFD65D0E, 0xFF928374),
        theme("solarized_light", "Solarized Light", ThemeGroup.LIGHT, false,
            0xFFFDF6E3, 0xFFFFFBEE, 0xFFEEE8D5, 0xFF475B62, 0xFF657B83, 0xFFD9D2BF, 0xFF268BD2, *SolarizedAccents),
        theme("rose_pine_dawn", "Rosé Pine Dawn", ThemeGroup.LIGHT, false,
            0xFFFAF4ED, 0xFFFFFAF3, 0xFFF2E9E1, 0xFF575279, 0xFF797593, 0xFFDFDAD9, 0xFF286983,
            0xFFB4637A, 0xFFD7827E, 0xFF6D8F5E, 0xFFC0729E, 0xFF286983, 0xFF56949F,
            0xFF907AA9, 0xFFEA9D34, 0xFF4A7AA3, 0xFFD2794E, 0xFF797593),
        theme("everforest_light", "Everforest Light", ThemeGroup.LIGHT, false,
            0xFFFDF6E3, 0xFFFFFBEF, 0xFFF4F0D9, 0xFF5C6A72, 0xFF829181, 0xFFE0DCC7, 0xFF35A77C,
            0xFFF85552, 0xFFF57D26, 0xFF8DA101, 0xFFDF69BA, 0xFF3A94C5, 0xFF35A77C,
            0xFF9B72CF, 0xFFDFA000, 0xFF4C7FB0, 0xFFC9706B, 0xFF939F91),
        theme("github_light", "GitHub Light", ThemeGroup.LIGHT, false,
            0xFFF6F8FA, 0xFFFFFFFF, 0xFFEAEEF2, 0xFF1F2328, 0xFF59636E, 0xFFD0D7DE, 0xFF0969DA,
            0xFFCF222E, 0xFFBC4C00, 0xFF1A7F37, 0xFFBF3989, 0xFF0969DA, 0xFF1B7C83,
            0xFF8250DF, 0xFF9A6700, 0xFF218BFF, 0xFFC4432B, 0xFF57606A),
        theme("ayu_light", "Ayu Light", ThemeGroup.LIGHT, false,
            0xFFF3F4F5, 0xFFFCFCFC, 0xFFE7E8E9, 0xFF5C6166, 0xFF787B80, 0xFFD8D8D7, 0xFFFA8D3E,
            0xFFF07171, 0xFFFA8D3E, 0xFF86B300, 0xFFD9679A, 0xFF399EE6, 0xFF4CBF99,
            0xFFA37ACC, 0xFFF2AE49, 0xFF55B4D4, 0xFFED9366, 0xFF8A9199),
        theme("tokyo_night_day", "Tokyo Night Day", ThemeGroup.LIGHT, false,
            0xFFE1E2E7, 0xFFE9E9ED, 0xFFD0D5E3, 0xFF3760BF, 0xFF6172B0, 0xFFC4C8DA, 0xFF2E7DE9,
            0xFFF52A65, 0xFFB15C00, 0xFF587539, 0xFFD20065, 0xFF2E7DE9, 0xFF118C74,
            0xFF9854F1, 0xFF8C6C3E, 0xFF007197, 0xFFC64343, 0xFF848CB5),
        theme("nord_light", "Nord Light", ThemeGroup.LIGHT, false,
            0xFFE5E9F0, 0xFFECEFF4, 0xFFD8DEE9, 0xFF2E3440, 0xFF4C566A, 0xFFC9D0DC, 0xFF5E81AC, *NordAccents),
        theme("one_light", "One Light", ThemeGroup.LIGHT, false,
            0xFFF0F0F1, 0xFFFAFAFA, 0xFFE5E5E6, 0xFF383A42, 0xFF696C77, 0xFFD3D3D4, 0xFF4078F2,
            0xFFE45649, 0xFFD7731A, 0xFF50A14F, 0xFFC13C93, 0xFF4078F2, 0xFF0184BC,
            0xFFA626A4, 0xFFC18401, 0xFF3BA3A6, 0xFF986801, 0xFF696C77),

        // ---- 10 dark ----
        theme("catppuccin_mocha", "Catppuccin Mocha", ThemeGroup.DARK, true,
            0xFF181825, 0xFF1E1E2E, 0xFF313244, 0xFFCDD6F4, 0xFFA6ADC8, 0xFF45475A, 0xFFCBA6F7,
            0xFFF38BA8, 0xFFFAB387, 0xFFA6E3A1, 0xFFF5C2E7, 0xFF89B4FA, 0xFF94E2D5,
            0xFFCBA6F7, 0xFFF9E2AF, 0xFF74C7EC, 0xFFF2CDCD, 0xFFB4BEFE),
        theme("dracula", "Dracula", ThemeGroup.DARK, true,
            0xFF21222C, 0xFF282A36, 0xFF343746, 0xFFF8F8F2, 0xFFB0B8D1, 0xFF44475A, 0xFFBD93F9,
            0xFFFF5555, 0xFFFFB86C, 0xFF50FA7B, 0xFFFF79C6, 0xFF8BE9FD, 0xFF6BE5C0,
            0xFFBD93F9, 0xFFF1FA8C, 0xFF8EA4E8, 0xFFFF92DF, 0xFFB0B8D1),
        theme("gruvbox_dark", "Gruvbox Dark", ThemeGroup.DARK, true,
            0xFF1D2021, 0xFF282828, 0xFF3C3836, 0xFFEBDBB2, 0xFFA89984, 0xFF504945, 0xFFFE8019,
            0xFFFB4934, 0xFFFE8019, 0xFFB8BB26, 0xFFD3869B, 0xFF83A598, 0xFF8EC07C,
            0xFFB16286, 0xFFFABD2F, 0xFF458588, 0xFFD5C4A1, 0xFFA89984),
        theme("solarized_dark", "Solarized Dark", ThemeGroup.DARK, true,
            0xFF002B36, 0xFF073642, 0xFF0E4552, 0xFFEEE8D5, 0xFF93A1A1, 0xFF2A5560, 0xFF268BD2, *SolarizedAccents),
        theme("tokyo_night", "Tokyo Night", ThemeGroup.DARK, true,
            0xFF16161E, 0xFF1A1B26, 0xFF292E42, 0xFFC0CAF5, 0xFF9AA5CE, 0xFF3B4261, 0xFF7AA2F7,
            0xFFF7768E, 0xFFFF9E64, 0xFF9ECE6A, 0xFFF7A1C4, 0xFF7AA2F7, 0xFF73DACA,
            0xFFBB9AF7, 0xFFE0AF68, 0xFF7DCFFF, 0xFF1ABC9C, 0xFFA9B1D6),
        theme("rose_pine", "Rosé Pine", ThemeGroup.DARK, true,
            0xFF191724, 0xFF1F1D2E, 0xFF26233A, 0xFFE0DEF4, 0xFF908CAA, 0xFF403D52, 0xFFC4A7E7,
            0xFFEB6F92, 0xFFEBBCBA, 0xFF8FBF9F, 0xFFE9A6C8, 0xFF6E9FC7, 0xFF9CCFD8,
            0xFFC4A7E7, 0xFFF6C177, 0xFF31748F, 0xFFF2A07B, 0xFF908CAA),
        theme("everforest_dark", "Everforest Dark", ThemeGroup.DARK, true,
            0xFF272E33, 0xFF2D353B, 0xFF3D484D, 0xFFD3C6AA, 0xFF9DA9A0, 0xFF475258, 0xFFA7C080,
            0xFFE67E80, 0xFFE69875, 0xFFA7C080, 0xFFD699B6, 0xFF7FBBB3, 0xFF83C092,
            0xFFB39DD6, 0xFFDBBC7F, 0xFF6FA3C4, 0xFFD3C6AA, 0xFF859289),
        theme("monokai_pro", "Monokai Pro", ThemeGroup.DARK, true,
            0xFF221F22, 0xFF2D2A2E, 0xFF403E41, 0xFFFCFCFA, 0xFF939293, 0xFF5B595C, 0xFFFFD866,
            0xFFFF6188, 0xFFFC9867, 0xFFA9DC76, 0xFFF48FB1, 0xFF78DCE8, 0xFF7AE3C3,
            0xFFAB9DF2, 0xFFFFD866, 0xFF6CB6FF, 0xFFE6A8FF, 0xFF939293),
        theme("kanagawa", "Kanagawa", ThemeGroup.DARK, true,
            0xFF16161D, 0xFF1F1F28, 0xFF2A2A37, 0xFFDCD7BA, 0xFFA09F95, 0xFF363646, 0xFF7E9CD8,
            0xFFE46876, 0xFFFFA066, 0xFF98BB6C, 0xFFD27E99, 0xFF7E9CD8, 0xFF7AA89F,
            0xFF957FB8, 0xFFE6C384, 0xFF7FB4CA, 0xFFA3D4D5, 0xFFC8C093),
        theme("palenight", "Material Palenight", ThemeGroup.DARK, true,
            0xFF242837, 0xFF292D3E, 0xFF3A3F58, 0xFFE4E7F7, 0xFF959DCB, 0xFF444267, 0xFFC792EA,
            0xFFF07178, 0xFFF78C6C, 0xFFC3E88D, 0xFFBB80B3, 0xFF82AAFF, 0xFF80CBC4,
            0xFFC792EA, 0xFFFFCB6B, 0xFF89DDFF, 0xFFC17E70, 0xFFB2CCD6),
    )

    fun find(id: String): AppTheme? = all.firstOrNull { it.id == id }

    /** Resolves a saved id (including System / Material You) to a concrete palette. */
    fun resolve(id: String, systemDark: Boolean): AppTheme = when (id) {
        SYSTEM_THEME, DYNAMIC_THEME -> if (systemDark) Dark else Light
        else -> find(id) ?: if (systemDark) Dark else Light
    }
}
