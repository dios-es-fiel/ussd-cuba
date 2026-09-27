package cu.ussd.cuba

import android.app.Activity
import android.content.Context
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

    val palettes = listOf(
        Palette("cuba", "Cuba (rojo)", 0xFFEF5350.toInt(), 0xFFC62828.toInt(), 0xFF5C1A1A.toInt(), 0xFFFFCDD2.toInt()),
        Palette("azul", "Océano", 0xFF42A5F5.toInt(), 0xFF1565C0.toInt(), 0xFF0D3B66.toInt(), 0xFFBBDEFB.toInt()),
        Palette("verde", "Palma", 0xFF66BB6A.toInt(), 0xFF2E7D32.toInt(), 0xFF1B3D1F.toInt(), 0xFFC8E6C9.toInt()),
        Palette("morado", "Orquídea", 0xFFAB47BC.toInt(), 0xFF6A1B9A.toInt(), 0xFF3A1A4A.toInt(), 0xFFE1BEE7.toInt()),
        Palette("naranja", "Atardecer", 0xFFFFA726.toInt(), 0xFFEF6C00.toInt(), 0xFF4A2C0A.toInt(), 0xFFFFE0B2.toInt()),
        Palette("turquesa", "Caribe", 0xFF26C6DA.toInt(), 0xFF00838F.toInt(), 0xFF0A3A40.toInt(), 0xFFB2EBF2.toInt())
    )

    fun applyNightMode(prefs: PrefsHelper) {
        when (prefs.getThemeMode()) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
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

    fun isDark(context: Context): Boolean {
        val mode = AppCompatDelegate.getDefaultNightMode()
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false
        val night = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun applyToActivity(activity: Activity, prefs: PrefsHelper) {
        applyNightMode(prefs)
        val dark = isDark(activity)
        activity.setTheme(paletteStyle(prefs.getPaletteId(), dark))
    }
}
