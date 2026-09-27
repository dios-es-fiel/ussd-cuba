package cu.ussd.cuba

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

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
        prefs.edit().putString("recents", list.take(10).joinToString(",")).apply()
    }

    fun getConfirmBeforeDial(): Boolean =
        prefs.getBoolean("confirm_before_dial", false)

    fun setConfirmBeforeDial(value: Boolean) {
        prefs.edit().putBoolean("confirm_before_dial", value).apply()
    }

    fun getThemeMode(): String = prefs.getString("theme_mode", "dark") ?: "dark"

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun getSavedPin(): String = prefs.getString("transfer_pin", "") ?: ""

    fun setSavedPin(pin: String) {
        prefs.edit().putString("transfer_pin", pin).apply()
    }

    fun getContacts(): List<Pair<String, String>> {
        val raw = prefs.getString("contacts", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("name") to o.getString("number")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContacts(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.take(8).forEach { (name, number) ->
            arr.put(org.json.JSONObject().apply {
                put("name", name)
                put("number", number)
            })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    fun addContact(name: String, number: String) {
        val list = getContacts().toMutableList()
        list.removeAll { it.second == number }
        list.add(0, name to number)
        saveContacts(list)
    }

    fun getShortcutIds(): List<String> {
        val raw = prefs.getString("shortcuts", "c1,c2,p1,p2") ?: "c1,c2,p1,p2"
        return raw.split(",").filter { it.isNotBlank() }.take(4)
    }

    fun setShortcutIds(ids: List<String>) {
        prefs.edit().putString("shortcuts", ids.take(4).joinToString(",")).apply()
    }

    fun getCopyInsteadOfDial(): Boolean =
        prefs.getBoolean("copy_instead_dial", false)

    fun setCopyInsteadOfDial(v: Boolean) {
        prefs.edit().putBoolean("copy_instead_dial", v).apply()
    }
}
