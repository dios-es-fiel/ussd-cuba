package cu.ussd.cuba

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ussd_cuba_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(id: String): Boolean {
        val current = getFavorites().toMutableSet()
        val added = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet("favorites", current).apply()
        return added
    }

    fun isFavorite(id: String): Boolean = getFavorites().contains(id)

    fun getRecents(): List<String> {
        val raw = prefs.getString("recents", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun addRecent(id: String) {
        val list = getRecents().toMutableList()
        list.remove(id)
        list.add(0, id)
        val trimmed = list.take(10)
        prefs.edit().putString("recents", trimmed.joinToString(",")).apply()
    }

    fun getConfirmBeforeDial(): Boolean =
        prefs.getBoolean("confirm_before_dial", false)

    fun setConfirmBeforeDial(value: Boolean) {
        prefs.edit().putBoolean("confirm_before_dial", value).apply()
    }
}
