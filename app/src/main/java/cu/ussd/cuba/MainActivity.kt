package cu.ussd.cuba

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import cu.ussd.cuba.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UssdAdapter
    private lateinit var prefs: PrefsHelper

    private var currentCategory = "Todos"
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsHelper(this)
        setSupportActionBar(binding.toolbar)

        adapter = UssdAdapter(
            onClick = { code -> handleCodeClick(code) },
            onLongClick = { code -> copyCode(code) },
            onFavoriteClick = { code -> toggleFavorite(code) },
            isFavorite = { id -> prefs.isFavorite(id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupShortcuts()
        setupChips()
        applyFilter()
    }

    private fun setupShortcuts() {
        val labels = listOf("Saldo", "Datos", "Planes", "Transferir")
        val buttons = listOf(
            binding.btnShortcut1,
            binding.btnShortcut2,
            binding.btnShortcut3,
            binding.btnShortcut4
        )
        CodesRepository.shortcuts.forEachIndexed { i, code ->
            buttons[i].text = labels[i]
            buttons[i].setOnClickListener { handleCodeClick(code) }
        }
    }

    private fun setupChips() {
        binding.chipGroup.removeAllViews()
        CodesRepository.categories.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = cat == "Todos"
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        currentCategory = cat
                        applyFilter()
                    }
                }
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun applyFilter() {
        val favs = prefs.getFavorites()
        val recents = prefs.getRecents()

        var list = when (currentCategory) {
            "Favoritos" -> CodesRepository.allCodes.filter { favs.contains(it.id) }
            "Recientes" -> recents.mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            "Todos" -> CodesRepository.allCodes
            else -> CodesRepository.allCodes.filter { it.category == currentCategory }
        }

        list = list.filter { CodesRepository.matchesQuery(it, currentQuery) }

        adapter.submitList(list)
        binding.tvCount.text = "${list.size} códigos"

        if (list.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.tvEmptyMessage.text = when {
                currentCategory == "Favoritos" -> "Aún no tienes favoritos.\nToca la estrella en cualquier código."
                currentCategory == "Recientes" -> "Aún no has marcado ningún código."
                currentQuery.isNotEmpty() -> "Sin resultados para \"$currentQuery\""
                else -> "No hay códigos en esta categoría."
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun handleCodeClick(code: UssdCode) {
        if (code.needsParams) {
            showParamsDialog(code)
        } else if (prefs.getConfirmBeforeDial()) {
            AlertDialog.Builder(this)
                .setTitle(code.title)
                .setMessage("¿Marcar ${code.code}?")
                .setPositiveButton("Marcar") { _, _ -> dial(code, code.code) }
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Copiar") { _, _ -> copyCode(code) }
                .show()
        } else {
            dial(code, code.code)
        }
    }

    private fun showParamsDialog(code: UssdCode) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val edits = mutableListOf<EditText>()
        code.paramHints.forEach { hint ->
            val et = EditText(this).apply {
                this.hint = hint
                inputType = when {
                    hint.contains("Monto", true) || hint.contains("clave", true) ||
                            hint.contains("Código", true) || hint.contains("Número", true) ->
                        InputType.TYPE_CLASS_NUMBER
                    else -> InputType.TYPE_CLASS_TEXT
                }
            }
            container.addView(et)
            edits.add(et)
        }

        AlertDialog.Builder(this)
            .setTitle(code.title)
            .setMessage(code.description)
            .setView(container)
            .setPositiveButton("Marcar") { _, _ ->
                val values = edits.map { it.text.toString().trim() }
                if (values.any { it.isEmpty() }) {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                var finalCode = code.code
                // Replace placeholders in order: {numero}, {clave}, {monto}, {codigo}, etc.
                val placeholders = Regex("\\{[^}]+\\}").findAll(code.code).map { it.value }.toList()
                placeholders.forEachIndexed { i, ph ->
                    if (i < values.size) {
                        finalCode = finalCode.replace(ph, values[i])
                    }
                }
                dial(code, finalCode)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dial(code: UssdCode, finalCode: String) {
        prefs.addRecent(code.id)
        val clean = finalCode.replace(" ", "")
        try {
            // ACTION_DIAL is more reliable for USSD on modern Android
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(clean)}")
            }
            startActivity(intent)
            Toast.makeText(this, "Abriendo marcador: $clean", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
        }
        // Refresh if viewing recents
        if (currentCategory == "Recientes") applyFilter()
    }

    private fun copyCode(code: UssdCode) {
        val display = code.code.replace(Regex("\\{[^}]+\\}"), "…")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("USSD", code.code))
        Toast.makeText(this, "Copiado: $display", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite(code: UssdCode) {
        val added = prefs.toggleFavorite(code.id)
        Toast.makeText(
            this,
            if (added) "Añadido a favoritos" else "Quitado de favoritos",
            Toast.LENGTH_SHORT
        ).show()
        adapter.notifyDataSetChanged()
        if (currentCategory == "Favoritos") applyFilter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Buscar: saldo, megas, recarga..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = query ?: ""
                applyFilter()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                applyFilter()
                return true
            }
        })

        // Sync confirm switch state
        val confirmItem = menu.findItem(R.id.action_confirm)
        confirmItem.isChecked = prefs.getConfirmBeforeDial()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_confirm -> {
                item.isChecked = !item.isChecked
                prefs.setConfirmBeforeDial(item.isChecked)
                Toast.makeText(
                    this,
                    if (item.isChecked) "Confirmación activada" else "Confirmación desactivada",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_theme -> {
                val night = AppCompatDelegate.getDefaultNightMode()
                if (night == AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
