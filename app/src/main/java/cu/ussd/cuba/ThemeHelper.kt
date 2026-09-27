package cu.ussd.cuba

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {

    data class Palette(
        val id: String,
        val name: String,
        val primaryDark: Int,
        val primaryLight: Int,
        val containerDark: Int,
        val containerLight: Int
    )

    /**
     * Estilos de interfaz (independientes de la paleta de color).
     * Pensados para el usuario promedio: nombres claros y cambios visibles.
     */
    data class UiStyle(
        val id: String,
        val name: String,
        val description: String,
        val cornerRadiusDp: Float,
        val cardElevationDp: Float,
        val itemMarginVDp: Int,
        val itemPaddingDp: Int,
        val titleSp: Float,
        val codeSp: Float,
        val descSp: Float,
        val showAccentBar: Boolean,
        val showDescription: Boolean,
        val strokeWidthDp: Float,
        val favButtonDp: Int,
        val listPaddingHDp: Int
    )

    val palettes = listOf(
        Palette("cuba", "Cuba (rojo)", 0xFFEF5350.toInt(), 0xFFC62828.toInt(), 0xFF5C1A1A.toInt(), 0xFFFFCDD2.toInt()),
        Palette("azul", "Océano", 0xFF42A5F5.toInt(), 0xFF1565C0.toInt(), 0xFF0D3B66.toInt(), 0xFFBBDEFB.toInt()),
        Palette("verde", "Palma", 0xFF66BB6A.toInt(), 0xFF2E7D32.toInt(), 0xFF1B3D1F.toInt(), 0xFFC8E6C9.toInt()),
        Palette("morado", "Orquídea", 0xFFAB47BC.toInt(), 0xFF6A1B9A.toInt(), 0xFF3A1A4A.toInt(), 0xFFE1BEE7.toInt()),
        Palette("naranja", "Atardecer", 0xFFFFA726.toInt(), 0xFFEF6C00.toInt(), 0xFF4A2C0A.toInt(), 0xFFFFE0B2.toInt()),
        Palette("turquesa", "Caribe", 0xFF26C6DA.toInt(), 0xFF00838F.toInt(), 0xFF0A3A40.toInt(), 0xFFB2EBF2.toInt())
    )

    val uiStyles = listOf(
        UiStyle(
            id = "clasico",
            name = "Clásico",
            description = "Como la mayoría de apps USSD (UtilEs / QvaCall)",
            cornerRadiusDp = 14f,
            cardElevationDp = 0f,
            itemMarginVDp = 4,
            itemPaddingDp = 12,
            titleSp = 13f,
            codeSp = 14f,
            descSp = 11f,
            showAccentBar = true,
            showDescription = true,
            strokeWidthDp = 0f,
            favButtonDp = 36,
            listPaddingHDp = 12
        ),
        UiStyle(
            id = "minimalista",
            name = "Minimalista",
            description = "Limpio, sin adornos, solo lo esencial",
            cornerRadiusDp = 8f,
            cardElevationDp = 0f,
            itemMarginVDp = 2,
            itemPaddingDp = 10,
            titleSp = 12f,
            codeSp = 15f,
            descSp = 10f,
            showAccentBar = false,
            showDescription = false,
            strokeWidthDp = 0f,
            favButtonDp = 32,
            listPaddingHDp = 16
        ),
        UiStyle(
            id = "profesional",
            name = "Profesional",
            description = "Refinado, como las mejores apps de productividad",
            cornerRadiusDp = 12f,
            cardElevationDp = 1f,
            itemMarginVDp = 5,
            itemPaddingDp = 14,
            titleSp = 13f,
            codeSp = 14f,
            descSp = 11f,
            showAccentBar = true,
            showDescription = true,
            strokeWidthDp = 0.5f,
            favButtonDp = 36,
            listPaddingHDp = 14
        ),
        UiStyle(
            id = "compacto",
            name = "Compacto",
            description = "Más códigos en pantalla, ideal si usas muchos",
            cornerRadiusDp = 6f,
            cardElevationDp = 0f,
            itemMarginVDp = 1,
            itemPaddingDp = 8,
            titleSp = 12f,
            codeSp = 13f,
            descSp = 10f,
            showAccentBar = true,
            showDescription = false,
            strokeWidthDp = 0f,
            favButtonDp = 30,
            listPaddingHDp = 8
        ),
        UiStyle(
            id = "comodo",
            name = "Cómodo",
            description = "Texto grande y botones amplios, fácil de tocar",
            cornerRadiusDp = 16f,
            cardElevationDp = 2f,
            itemMarginVDp = 6,
            itemPaddingDp = 16,
            titleSp = 15f,
            codeSp = 16f,
            descSp = 13f,
            showAccentBar = true,
            showDescription = true,
            strokeWidthDp = 0f,
            favButtonDp = 44,
            listPaddingHDp = 12
        ),
        UiStyle(
            id = "tarjetas",
            name = "Tarjetas",
            description = "Tarjetas elevadas y redondeadas, aspecto amigable",
            cornerRadiusDp = 20f,
            cardElevationDp = 4f,
            itemMarginVDp = 8,
            itemPaddingDp = 14,
            titleSp = 13f,
            codeSp = 15f,
            descSp = 11f,
            showAccentBar = false,
            showDescription = true,
            strokeWidthDp = 0f,
            favButtonDp = 40,
            listPaddingHDp = 16
        )
    )

    fun uiStyle(id: String): UiStyle =
        uiStyles.find { it.id == id } ?: uiStyles.first()

    fun applyNightMode(prefs: PrefsHelper) {
        val mode = when (prefs.getThemeMode()) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun paletteStyle(paletteId: String, dark: Boolean): Int {
        return when (paletteId) {
            "azul" -> if (dark) R.style.Theme_UssdCuba_Azul_Dark else R.style.Theme_UssdCuba_Azul_Light
            "verde" -> if (dark) R.style.Theme_UssdCuba_Verde_Dark else R.style.Theme_UssdCuba_Verde_Light
            "morado" -> if (dark) R.style.Theme_UssdCuba_Morado_Dark else R.style.Theme_UssdCuba_Morado_Light
            "naranja" -> if (dark) R.style.Theme_UssdCuba_Naranja_Dark else R.style.Theme_UssdCuba_Naranja_Light
            "turquesa" -> if (dark) R.style.Theme_UssdCuba_Turquesa_Dark else R.style.Theme_UssdCuba_Turquesa_Light
            else -> if (dark) R.style.Theme_UssdCuba_Cuba_Dark else R.style.Theme_UssdCuba_Cuba_Light
        }
    }

    fun isDark(context: Context, prefs: PrefsHelper? = null): Boolean {
        if (prefs != null) {
            return when (prefs.getThemeMode()) {
                "light" -> false
                "dark" -> true
                else -> {
                    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }
        }
        val mode = AppCompatDelegate.getDefaultNightMode()
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    fun applyToActivity(activity: Activity, prefs: PrefsHelper) {
        applyNightMode(prefs)
        val dark = isDark(activity, prefs)
        activity.setTheme(paletteStyle(prefs.getPaletteId(), dark))
    }
}
