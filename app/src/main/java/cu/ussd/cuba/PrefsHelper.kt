package cu.ussd.cuba

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PrefsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ussd_cuba_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(id: String): Boolean {
        val current = getFavorites().toMutableSet()
        val added = if (id in current) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        prefs.edit().putStringSet("favorites", current).apply()
        return added
    }

    fun isFavorite(id: String) = id in getFavorites()

    fun getRecents(): List<String> {
        val raw = prefs.getString("recents", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun addRecent(id: String) {
        val list = getRecents().toMutableList()
        list.remove(id)
        list.add(0, id)
        prefs.edit().putString("recents", list.take(15).joinToString(",")).apply()
        bumpUsage(id)
    }

    fun bumpUsage(id: String) {
        val key = "use_$id"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun getUsage(id: String) = prefs.getInt("use_$id", 0)

    fun getMostUsed(limit: Int = 8): List<String> {
        return CodesRepository.allCodes
            .map { it.id to getUsage(it.id) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun getConfirmBeforeDial() = prefs.getBoolean("confirm_before_dial", false)
    fun setConfirmBeforeDial(v: Boolean) = prefs.edit().putBoolean("confirm_before_dial", v).apply()

    fun getCopyInsteadOfDial() = prefs.getBoolean("copy_instead_dial", false)
    fun setCopyInsteadOfDial(v: Boolean) = prefs.edit().putBoolean("copy_instead_dial", v).apply()

    fun getUseCallAction() = prefs.getBoolean("use_call_action", false)
    fun setUseCallAction(v: Boolean) = prefs.edit().putBoolean("use_call_action", v).apply()

    fun getDisableSwipe() = prefs.getBoolean("disable_swipe", true)
    fun setDisableSwipe(v: Boolean) = prefs.edit().putBoolean("disable_swipe", v).apply()

    fun getThemeMode() = prefs.getString("theme_mode", "dark") ?: "dark"
    fun setThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()

    fun getPaletteId() = prefs.getString("palette_id", "cuba") ?: "cuba"
    fun setPaletteId(id: String) = prefs.edit().putString("palette_id", id).apply()

    fun getSavedPin() = prefs.getString("transfer_pin", "") ?: ""
    fun setSavedPin(pin: String) = prefs.edit().putString("transfer_pin", pin).apply()

    fun getContacts(): List<Pair<String, String>> {
        val raw = prefs.getString("contacts", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("name") to o.getString("number")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveContacts(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.take(12).forEach { (name, number) ->
            arr.put(JSONObject().apply {
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

    fun setShortcutIds(ids: List<String>) =
        prefs.edit().putString("shortcuts", ids.take(4).joinToString(",")).apply()

    /** Templates: name, number, amount */
    fun getTemplates(): List<Triple<String, String, String>> {
        val raw = prefs.getString("templates", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Triple(o.getString("name"), o.getString("number"), o.optString("amount", ""))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTemplates(list: List<Triple<String, String, String>>) {
        val arr = JSONArray()
        list.take(10).forEach { (n, num, amt) ->
            arr.put(JSONObject().apply {
                put("name", n); put("number", num); put("amount", amt)
            })
        }
        prefs.edit().putString("templates", arr.toString()).apply()
    }

    fun exportFavoritesJson(): String {
        val favs = getFavorites()
        val arr = JSONArray()
        CodesRepository.allCodes.filter { it.id in favs }.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("code", c.code)
            })
        }
        return arr.toString(2)
    }

    fun importFavoriteIds(json: String): Int {
        return try {
            val arr = JSONArray(json)
            val ids = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isNotBlank()) ids.add(id)
            }
            val merged = getFavorites().toMutableSet().apply { addAll(ids) }
            prefs.edit().putStringSet("favorites", merged).apply()
            ids.size
        } catch (_: Exception) {
            0
        }
    }

    fun getLastTab() = prefs.getInt("last_tab", 0)
    fun setLastTab(i: Int) = prefs.edit().putInt("last_tab", i).apply()
}
