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
import android.widget.ArrayAdapter
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

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var favAdapter: UssdAdapter
    private lateinit var recentAdapter: UssdAdapter
    private lateinit var mostAdapter: UssdAdapter
    private val vm: AppViewModel by activityViewModels()
    private var query = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = requireActivity() as MainActivity
        favAdapter = ad(act); recentAdapter = ad(act); mostAdapter = ad(act)
        b.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        b.rvRecents.layoutManager = LinearLayoutManager(requireContext())
        b.rvMostUsed.layoutManager = LinearLayoutManager(requireContext())
        b.rvFavorites.adapter = favAdapter
        b.rvRecents.adapter = recentAdapter
        b.rvMostUsed.adapter = mostAdapter
        b.rvFavorites.itemAnimator = null
        b.rvRecents.itemAnimator = null
        b.rvMostUsed.itemAnimator = null
        b.btnEmergency.setOnClickListener { act.showEmergencyPanel() }
        b.tvSeeAllFav.setOnClickListener { act.showAllFavorites() }
        setupShortcuts()
        vm.query.observe(viewLifecycleOwner) { query = it; refresh() }
        vm.tick.observe(viewLifecycleOwner) { refresh() }
        refresh()
    }

    private fun ad(act: MainActivity) = UssdAdapter(
        { act.handleCodeClick(it) }, { act.copyCode(it) },
        { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) }
    )

    private fun setupShortcuts() {
        val act = requireActivity() as MainActivity
        val grid = b.gridShortcuts
        grid.removeAllViews()
        val icons = listOf("💰", "📡", "📦", "🔄")
        val codes = act.prefs.getShortcutIds()
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .ifEmpty { CodesRepository.shortcuts }
        codes.forEachIndexed { i, code ->
            val item = layoutInflater.inflate(R.layout.item_shortcut, grid, false) as MaterialCardView
            item.layoutParams = GridLayout.LayoutParams().apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 2, 1f)
                setMargins(6, 6, 6, 6)
            }
            item.findViewById<TextView>(R.id.tvIcon).text = icons.getOrElse(i) { "☆" }
            item.findViewById<TextView>(R.id.tvLabel).text = when (code.id) {
                "c1" -> "Saldo"; "c2" -> "Datos"; "p1" -> "Planes"; "p2" -> "Transferir"
                else -> code.title.take(14)
            }
            item.findViewById<TextView>(R.id.tvCodeHint).text = code.code
            item.setOnClickListener { act.handleCodeClick(code) }
            item.setOnLongClickListener {
                val opts = CodesRepository.allCodes.map { "${it.title} (${it.code})" }.toTypedArray()
                AlertDialog.Builder(requireContext()).setTitle("Elegir acceso").setItems(opts) { _, w ->
                    val ids = act.prefs.getShortcutIds().toMutableList()
                    while (ids.size < 4) ids.add("c1")
                    ids[i] = CodesRepository.allCodes[w].id
                    act.prefs.setShortcutIds(ids)
                    setupShortcuts()
                }.show()
                true
            }
            grid.addView(item)
        }
    }

    fun refresh() {
        if (_b == null) return
        val act = requireActivity() as MainActivity
        val favs = act.prefs.getFavorites()
        var favList = CodesRepository.allCodes.filter { it.id in favs }
        if (query.isNotEmpty()) favList = favList.filter { CodesRepository.matchesQuery(it, query) }
        favAdapter.submitList(favList.take(6))
        b.tvFavEmpty.isVisible = favList.isEmpty()
        b.tvSeeAllFav.isVisible = favList.size > 6 || favs.size > 6

        var most = act.prefs.getMostUsed(6)
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
        if (query.isNotEmpty()) most = most.filter { CodesRepository.matchesQuery(it, query) }
        mostAdapter.submitList(most)
        b.tvMostEmpty.isVisible = most.isEmpty()

        var rec = act.prefs.getRecents()
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }.take(5)
        if (query.isNotEmpty()) rec = rec.filter { CodesRepository.matchesQuery(it, query) }
        recentAdapter.submitList(rec)
        b.tvRecentsEmpty.isVisible = rec.isEmpty()
        b.rvRecents.isVisible = rec.isNotEmpty()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class ListFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: UssdAdapter
    private var category = "Consultas"
    private val vm: AppViewModel by activityViewModels()
    private var query = ""

    companion object {
        fun newInstance(cat: String) = ListFragment().apply {
            arguments = Bundle().apply { putString("category", cat) }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        category = arguments?.getString("category") ?: "Consultas"
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val act = requireActivity() as MainActivity
        adapter = UssdAdapter(
            { act.handleCodeClick(it) }, { act.copyCode(it) },
            { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        b.recyclerView.itemAnimator = null
        vm.query.observe(viewLifecycleOwner) { query = it; apply() }
        vm.tick.observe(viewLifecycleOwner) { apply() }
        apply()
    }

    private fun base() = when (category) {
        "Consultas" -> CodesRepository.allCodes.filter { it.category == "Consultas" }
        "Planes" -> CodesRepository.allCodes.filter { it.category in listOf("Planes", "Recargas") }
        "Llamadas" -> CodesRepository.allCodes.filter { it.category in listOf("Llamadas", "Internacional") }
        else -> CodesRepository.allCodes
    }

    private fun apply() {
        if (_b == null) return
        val list = base().filter { CodesRepository.matchesQuery(it, query) }
        adapter.submitList(list)
        b.emptyState.isVisible = list.isEmpty()
        b.recyclerView.isVisible = list.isNotEmpty()
        b.tvEmpty.text = if (query.isNotEmpty()) "Sin resultados" else "Sin códigos"
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class MasFragment : Fragment() {
    private var _b: FragmentMasBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: UssdAdapter
    private val vm: AppViewModel by activityViewModels()
    private var query = ""; private var sub = "Transfermóvil"

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMasBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val act = requireActivity() as MainActivity
        adapter = UssdAdapter(
            { act.handleCodeClick(it) }, { act.copyCode(it) },
            { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        b.recyclerView.itemAnimator = null
        b.chipTm.setOnClickListener { sub = "Transfermóvil"; sync(); apply() }
        b.chipAtencion.setOnClickListener { sub = "Atención"; sync(); apply() }
        b.chipEmergencias.setOnClickListener { sub = "Emergencias"; sync(); apply() }
        b.chipDispositivo.setOnClickListener { sub = "Dispositivo"; sync(); apply() }
        vm.query.observe(viewLifecycleOwner) { query = it; apply() }
        vm.tick.observe(viewLifecycleOwner) { apply() }
        sync(); apply()
    }

    private fun sync() {
        b.chipTm.isChecked = sub == "Transfermóvil"
        b.chipAtencion.isChecked = sub == "Atención"
        b.chipEmergencias.isChecked = sub == "Emergencias"
        b.chipDispositivo.isChecked = sub == "Dispositivo"
    }

    private fun apply() {
        if (_b == null) return
        val list = CodesRepository.allCodes.filter { it.category == sub }
            .filter { CodesRepository.matchesQuery(it, query) }
        adapter.submitList(list)
        b.emptyState.isVisible = list.isEmpty()
        b.recyclerView.isVisible = list.isNotEmpty()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val act = requireActivity() as MainActivity
        val p = act.prefs

        val themes = listOf("Oscuro", "Claro", "Sistema")
        b.spinnerTheme.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, themes)
        b.spinnerTheme.setSelection(when (p.getThemeMode()) {
            "light" -> 1; "system" -> 2; else -> 0
        })

        val pals = ThemeHelper.palettes.map { it.name }
        b.spinnerPalette.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, pals)
        b.spinnerPalette.setSelection(
            ThemeHelper.palettes.indexOfFirst { it.id == p.getPaletteId() }.coerceAtLeast(0)
        )

        b.switchConfirm.isChecked = p.getConfirmBeforeDial()
        b.switchCopy.isChecked = p.getCopyInsteadOfDial()
        b.switchCall.isChecked = p.getUseCallAction()
        b.switchSwipe.isChecked = p.getDisableSwipe()

        b.switchConfirm.setOnCheckedChangeListener { _, v -> p.setConfirmBeforeDial(v) }
        b.switchCopy.setOnCheckedChangeListener { _, v -> p.setCopyInsteadOfDial(v) }
        b.switchCall.setOnCheckedChangeListener { _, v -> p.setUseCallAction(v) }
        b.switchSwipe.setOnCheckedChangeListener { _, v ->
            p.setDisableSwipe(v)
            act.findViewById<ViewPager2>(R.id.viewPager)?.isUserInputEnabled = !v
        }

        b.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val mode = when (pos) { 1 -> "light"; 2 -> "system"; else -> "dark" }
                if (mode != p.getThemeMode()) {
                    p.setThemeMode(mode)
                    act.recreateWithTheme()
                }
            }
        }

        b.spinnerPalette.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val pid = ThemeHelper.palettes[pos].id
                if (pid != p.getPaletteId()) {
                    p.setPaletteId(pid)
                    act.recreateWithTheme()
                }
            }
        }

        b.btnPin.setOnClickListener {
            val et = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setText(p.getSavedPin()); setPadding(48, 32, 48, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("PIN transferencia")
                .setMessage("Solo en este teléfono, sin cifrar.")
                .setView(et)
                .setPositiveButton("Guardar") { _, _ -> p.setSavedPin(et.text.toString().trim()) }
                .setNegativeButton("Borrar") { _, _ -> p.setSavedPin("") }
                .setNeutralButton("Cerrar", null).show()
        }

        b.btnContacts.setOnClickListener {
            val contacts = p.getContacts()
            val labels = contacts.map { "${it.first} — ${it.second}" }.ifEmpty { listOf("(vacío)") }
            AlertDialog.Builder(requireContext()).setTitle("Contactos")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (contacts.isEmpty()) return@setItems
                    AlertDialog.Builder(requireContext()).setTitle(contacts[which].first)
                        .setPositiveButton("Eliminar") { _, _ ->
                            p.saveContacts(contacts.filterIndexed { i, _ -> i != which })
                        }.setNegativeButton("Cerrar", null).show()
                }
                .setPositiveButton("Añadir") { _, _ ->
                    val box = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
                    }
                    val n = EditText(requireContext()).apply { hint = "Nombre" }
                    val num = EditText(requireContext()).apply {
                        hint = "Número"; inputType = InputType.TYPE_CLASS_PHONE
                    }
                    box.addView(n); box.addView(num)
                    AlertDialog.Builder(requireContext()).setTitle("Nuevo").setView(box)
                        .setPositiveButton("Guardar") { _, _ ->
                            if (num.text.length >= 6)
                                p.addContact(n.text.toString().ifBlank { num.text.toString() }, num.text.toString())
                        }.setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cerrar", null).show()
        }

        b.btnTemplates.setOnClickListener {
            val t = p.getTemplates()
            val labels = t.map { "${it.first}: ${it.second} / ${it.third}" }.ifEmpty { listOf("(vacío)") }
            AlertDialog.Builder(requireContext()).setTitle("Plantillas transferencia")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (t.isEmpty()) return@setItems
                    p.saveTemplates(t.filterIndexed { i, _ -> i != which })
                    Toast.makeText(requireContext(), "Eliminada", Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton("Añadir") { _, _ ->
                    val box = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
                    }
                    val n = EditText(requireContext()).apply { hint = "Nombre (ej. Mamá)" }
                    val num = EditText(requireContext()).apply {
                        hint = "Número"; inputType = InputType.TYPE_CLASS_PHONE
                    }
                    val amt = EditText(requireContext()).apply {
                        hint = "Monto CUP"; inputType = InputType.TYPE_CLASS_NUMBER
                    }
                    box.addView(n); box.addView(num); box.addView(amt)
                    AlertDialog.Builder(requireContext()).setTitle("Plantilla").setView(box)
                        .setPositiveButton("Guardar") { _, _ ->
                            if (num.text.length >= 6) {
                                val list = t.toMutableList()
                                list.add(0, Triple(n.text.toString().ifBlank { "Sin nombre" },
                                    num.text.toString(), amt.text.toString()))
                                p.saveTemplates(list)
                            }
                        }.setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cerrar", null).show()
        }

        b.btnExport.setOnClickListener {
            act.copyRaw(p.exportFavoritesJson())
            Toast.makeText(requireContext(), "JSON de favoritos copiado", Toast.LENGTH_LONG).show()
        }

        b.btnImport.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = "Pega el JSON aquí"; minLines = 4; setPadding(32, 24, 32, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("Importar favoritos").setView(et)
                .setPositiveButton("Importar") { _, _ ->
                    val n = p.importFavoriteIds(et.text.toString())
                    Toast.makeText(requireContext(), "Importados: $n", Toast.LENGTH_SHORT).show()
                    act.notifyRefresh()
                }.setNegativeButton("Cancelar", null).show()
        }

        b.btnChecklist.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Después de recargar")
                .setMessage(
                    "1. Marca *222# — verifica saldo\n" +
                    "2. Marca *222*732# — límite nacional 360 CUP\n" +
                    "3. Si compraste plan: *222*328# datos / *133# menú"
                )
                .setPositiveButton("*222#") { _, _ ->
                    CodesRepository.allCodes.find { it.id == "c1" }?.let { act.handleCodeClick(it) }
                }
                .setNeutralButton("*222*732#") { _, _ ->
                    CodesRepository.allCodes.find { it.id == "c6" }?.let { act.handleCodeClick(it) }
                }
                .setNegativeButton("Cerrar", null).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
