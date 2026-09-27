package cu.ussd.cuba

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import cu.ussd.cuba.databinding.ActivityMainBinding
import cu.ussd.cuba.databinding.FragmentHomeBinding
import cu.ussd.cuba.databinding.FragmentListBinding
import cu.ussd.cuba.databinding.FragmentMasBinding
import cu.ussd.cuba.databinding.FragmentSettingsBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var prefs: PrefsHelper
    private val viewModel: AppViewModel by viewModels()
    private var updatingNav = false

    private val pageTitles = listOf("Inicio", "Consultas", "Planes", "Llamadas", "Más", "Ajustes")

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PrefsHelper(this)
        ThemeHelper.applyNightMode(prefs)
        ThemeHelper.applyToActivity(this, prefs)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = PagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.setPageTransformer(null)
        binding.viewPager.isUserInputEnabled = !prefs.getDisableSwipe()

        val start = prefs.getLastTab().coerceIn(0, 5)
        binding.viewPager.setCurrentItem(start, false)
        binding.toolbar.title = pageTitles[start]

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (updatingNav) return@setOnItemSelectedListener true
            val index = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_consultas -> 1
                R.id.nav_planes -> 2
                R.id.nav_llamadas -> 3
                R.id.nav_mas -> 4
                R.id.nav_settings -> 5
                else -> 0
            }
            if (binding.viewPager.currentItem != index) {
                binding.viewPager.setCurrentItem(index, false)
            }
            binding.toolbar.title = pageTitles[index]
            prefs.setLastTab(index)
            binding.searchCard.isVisible = index != 5
            true
        }

        val ids = listOf(
            R.id.nav_home, R.id.nav_consultas, R.id.nav_planes,
            R.id.nav_llamadas, R.id.nav_mas, R.id.nav_settings
        )
        updatingNav = true
        binding.bottomNav.selectedItemId = ids[start]
        updatingNav = false
        binding.searchCard.isVisible = start != 5

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatingNav = true
                binding.bottomNav.selectedItemId = ids[position]
                binding.toolbar.title = pageTitles[position]
                prefs.setLastTab(position)
                binding.searchCard.isVisible = position != 5
                updatingNav = false
            }
        })

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                binding.btnClearSearch.isVisible = q.isNotEmpty()
                viewModel.setQuery(q)
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.setQuery("")
        }

        handleDialIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        val code = intent?.getStringExtra("ussd_code") ?: return
        dialRaw(code)
    }

    fun notifyRefresh() {
        viewModel.notifyDataChanged()
    }

    fun handleCodeClick(code: UssdCode) {
        if (prefs.getCopyInsteadOfDial()) {
            if (code.needsParams) showParamsDialog(code, copyOnly = true)
            else copyRaw(code.code)
            return
        }
        if (code.needsParams) {
            showParamsDialog(code, copyOnly = false)
        } else if (prefs.getConfirmBeforeDial()) {
            AlertDialog.Builder(this)
                .setTitle(code.title)
                .setMessage("¿Marcar ${code.code}?")
                .setPositiveButton("Marcar") { _, _ -> dial(code, code.code) }
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Copiar") { _, _ -> copyRaw(code.code) }
                .show()
        } else {
            dial(code, code.code)
        }
    }

    private fun showParamsDialog(code: UssdCode, copyOnly: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val edits = mutableListOf<EditText>()
        val contacts = prefs.getContacts()
        val savedPin = prefs.getSavedPin()
        val templates = prefs.getTemplates()

        if (code.id == "p3" && templates.isNotEmpty()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            templates.take(4).forEach { (name, number, amount) ->
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = name
                    tag = Triple(name, number, amount)
                }
                row.addView(chip)
            }
            container.addView(row)
            container.tag = row
        }

        code.paramHints.forEach { hint ->
            val et = EditText(this).apply {
                this.hint = hint
                inputType = when {
                    hint.contains("Clave", true) || hint.contains("PIN", true) ->
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    else -> InputType.TYPE_CLASS_NUMBER
                }
                if (hint.contains("Clave", true) && savedPin.isNotEmpty() &&
                    !hint.contains("nueva", true)
                ) setText(savedPin)
            }
            container.addView(et)
            edits.add(et)
            if (hint.contains("Número", true) && contacts.isNotEmpty()) {
                val contactRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                contacts.take(4).forEach { (name, number) ->
                    contactRow.addView(com.google.android.material.chip.Chip(this).apply {
                        text = name.ifBlank { number.takeLast(4) }
                        setOnClickListener { et.setText(number) }
                    })
                }
                container.addView(contactRow)
            }
        }

        (container.tag as? LinearLayout)?.let { row ->
            for (i in 0 until row.childCount) {
                val chip = row.getChildAt(i) as com.google.android.material.chip.Chip
                @Suppress("UNCHECKED_CAST")
                val t = chip.tag as Triple<String, String, String>
                chip.setOnClickListener {
                    if (edits.isNotEmpty()) edits[0].setText(t.second)
                    if (edits.size > 2 && t.third.isNotEmpty()) edits[2].setText(t.third)
                    if (edits.size > 1 && savedPin.isNotEmpty()) edits[1].setText(savedPin)
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(code.title)
            .setMessage(code.description)
            .setView(container)
            .setPositiveButton(if (copyOnly) "Copiar" else "Marcar") { _, _ ->
                val values = edits.map { it.text.toString().trim() }
                if (values.any { it.isEmpty() }) {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                code.paramHints.forEachIndexed { i, hint ->
                    if (hint.contains("Número", true) && values[i].length >= 8) {
                        prefs.addContact(values[i], values[i])
                    }
                    if (hint.contains("nueva", true)) prefs.setSavedPin(values[i])
                    else if (hint.contains("Clave", true) && prefs.getSavedPin().isEmpty()) {
                        prefs.setSavedPin(values[i])
                    }
                }
                var finalCode = code.code
                Regex("\\{[^}]+\\}").findAll(code.code).map { it.value }.toList()
                    .forEachIndexed { i, ph ->
                        if (i < values.size) finalCode = finalCode.replace(ph, values[i])
                    }
                if (copyOnly) copyRaw(finalCode) else dial(code, finalCode)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun dial(code: UssdCode, finalCode: String) {
        prefs.addRecent(code.id)
        viewModel.notifyDataChanged()
        dialRaw(finalCode)
    }

    private fun dialRaw(finalCode: String) {
        val clean = finalCode.replace(" ", "")
        try {
            if (prefs.getUseCallAction() &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startActivity(Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(clean)}")
                })
            } else {
                if (prefs.getUseCallAction() &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.CALL_PHONE), 200
                    )
                }
                startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(clean)}")
                })
            }
            Toast.makeText(this, clean, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyCode(code: UssdCode) {
        if (code.needsParams) showParamsDialog(code, copyOnly = true)
        else copyRaw(code.code)
    }

    fun copyRaw(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("USSD", text))
        Toast.makeText(this, "Copiado: $text", Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(code: UssdCode) {
        val added = prefs.toggleFavorite(code.id)
        Toast.makeText(this, if (added) "Favorito ★" else "Quitado", Toast.LENGTH_SHORT).show()
        viewModel.notifyDataChanged()
    }

    fun showEmergencyPanel() {
        val emerg = CodesRepository.allCodes.filter { it.category == "Emergencias" }
        val labels = emerg.map { "${it.title}  ${it.code}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Emergencias")
            .setItems(labels) { _, which -> handleCodeClick(emerg[which]) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun showAllFavorites() {
        val favs = prefs.getFavorites()
        val list = CodesRepository.allCodes.filter { it.id in favs }
        if (list.isEmpty()) {
            Toast.makeText(this, "No hay favoritos", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Todos los favoritos (${list.size})")
            .setItems(list.map { "${it.title}\n${it.code}" }.toTypedArray()) { _, w ->
                handleCodeClick(list[w])
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun recreateWithTheme() {
        recreate()
    }

    private inner class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 6
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> HomeFragment()
            1 -> ListFragment.newInstance("Consultas")
            2 -> ListFragment.newInstance("Planes")
            3 -> ListFragment.newInstance("Llamadas")
            4 -> MasFragment()
            else -> SettingsFragment()
        }
    }
}
