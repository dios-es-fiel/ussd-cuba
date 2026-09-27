package cu.ussd.cuba

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayoutMediator
import cu.ussd.cuba.databinding.ActivityMainBinding
import cu.ussd.cuba.databinding.FragmentHomeBinding
import cu.ussd.cuba.databinding.FragmentListBinding
import cu.ussd.cuba.databinding.FragmentMasBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var prefs: PrefsHelper
    private val viewModel: AppViewModel by viewModels()
    private var updatingNav = false

    private val pageTitles = listOf("Inicio", "Consultas", "Planes", "Llamadas", "Más")

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsHelper(this)
        setSupportActionBar(binding.toolbar)

        // Solid background prevents page transition "ghost trails"
        binding.root.setBackgroundColor(
            resources.getColor(android.R.color.transparent, theme).let {
                // force opaque surface
                val typed = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
                val bg = typed.getColor(0, 0xFF121212.toInt())
                typed.recycle()
                binding.root.setBackgroundColor(bg)
                bg
            }
        )

        binding.viewPager.adapter = PagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.isUserInputEnabled = true
        // Disable fancy transforms that leave trails on some devices
        binding.viewPager.setPageTransformer(null)

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (updatingNav) return@setOnItemSelectedListener true
            val index = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_consultas -> 1
                R.id.nav_planes -> 2
                R.id.nav_llamadas -> 3
                R.id.nav_mas -> 4
                else -> 0
            }
            if (binding.viewPager.currentItem != index) {
                // false = no smooth scroll → avoids permanent trail artifacts
                binding.viewPager.setCurrentItem(index, false)
            }
            binding.toolbar.title = pageTitles[index]
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatingNav = true
                val ids = listOf(
                    R.id.nav_home, R.id.nav_consultas, R.id.nav_planes,
                    R.id.nav_llamadas, R.id.nav_mas
                )
                binding.bottomNav.selectedItemId = ids[position]
                binding.toolbar.title = pageTitles[position]
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
    }

    private fun applyThemeFromPrefs() {
        val mode = PrefsHelper(this).getThemeMode()
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    fun handleCodeClick(code: UssdCode) {
        if (prefs.getCopyInsteadOfDial()) {
            copyCode(code)
            return
        }
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
        val contacts = prefs.getContacts()
        val savedPin = prefs.getSavedPin()

        code.paramHints.forEach { hint ->
            val et = EditText(this).apply {
                this.hint = hint
                inputType = when {
                    hint.contains("Clave", true) || hint.contains("PIN", true) ->
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    else -> InputType.TYPE_CLASS_NUMBER
                }
                // Prefill PIN
                if (hint.contains("Clave", true) && savedPin.isNotEmpty() &&
                    !hint.contains("nueva", true) && !hint.contains("actual", true)
                ) {
                    setText(savedPin)
                }
                if (hint.contains("actual", true) && savedPin.isNotEmpty()) {
                    setText(savedPin)
                }
            }
            container.addView(et)
            edits.add(et)

            // Contact chips for number fields
            if (hint.contains("Número", true) && contacts.isNotEmpty()) {
                val contactRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 4, 0, 8)
                }
                contacts.take(4).forEach { (name, number) ->
                    val chip = com.google.android.material.chip.Chip(this).apply {
                        text = name.ifBlank { number.takeLast(4) }
                        isClickable = true
                        setOnClickListener { et.setText(number) }
                    }
                    contactRow.addView(chip)
                }
                container.addView(contactRow)
            }
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
                // Save number as contact if looks like phone
                code.paramHints.forEachIndexed { i, hint ->
                    if (hint.contains("Número", true) && values[i].length >= 8) {
                        prefs.addContact(values[i].takeLast(4), values[i])
                    }
                    if (hint.contains("Clave", true) && !hint.contains("nueva", true) &&
                        values[i].isNotEmpty()
                    ) {
                        // optional: don't auto-overwrite; only if empty saved
                        if (prefs.getSavedPin().isEmpty()) prefs.setSavedPin(values[i])
                    }
                    if (hint.contains("nueva", true) && values[i].isNotEmpty()) {
                        prefs.setSavedPin(values[i])
                    }
                }
                var finalCode = code.code
                val placeholders = Regex("\\{[^}]+\\}").findAll(code.code).map { it.value }.toList()
                placeholders.forEachIndexed { i, ph ->
                    if (i < values.size) finalCode = finalCode.replace(ph, values[i])
                }
                dial(code, finalCode)
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Copiar plantilla") { _, _ ->
                copyRaw(code.code)
            }
            .show()
    }

    fun dial(code: UssdCode, finalCode: String) {
        prefs.addRecent(code.id)
        val clean = finalCode.replace(" ", "")
        try {
            startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(clean)}")
            })
            Toast.makeText(this, clean, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
        }
        notifyDataChanged()
    }

    fun copyCode(code: UssdCode) {
        if (code.needsParams) {
            showParamsDialog(code)
            Toast.makeText(this, "Completa los datos; luego puedes copiar", Toast.LENGTH_SHORT).show()
            return
        }
        copyRaw(code.code)
    }

    private fun copyRaw(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("USSD", text))
        Toast.makeText(this, "Copiado: $text", Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(code: UssdCode) {
        val added = prefs.toggleFavorite(code.id)
        Toast.makeText(this, if (added) "Favorito ★" else "Quitado", Toast.LENGTH_SHORT).show()
        notifyDataChanged()
    }

    private fun notifyDataChanged() {
        supportFragmentManager.fragments.forEach { frag ->
            when (frag) {
                is HomeFragment -> frag.refresh()
                is ListFragment -> frag.reload()
                is MasFragment -> frag.reload()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_confirm).isChecked = prefs.getConfirmBeforeDial()
        menu.findItem(R.id.action_copy_mode).isChecked = prefs.getCopyInsteadOfDial()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_confirm -> {
                item.isChecked = !item.isChecked
                prefs.setConfirmBeforeDial(item.isChecked)
                true
            }
            R.id.action_copy_mode -> {
                item.isChecked = !item.isChecked
                prefs.setCopyInsteadOfDial(item.isChecked)
                Toast.makeText(
                    this,
                    if (item.isChecked) "Modo copiar activado" else "Modo marcar activado",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            R.id.action_pin -> {
                showPinDialog()
                true
            }
            R.id.action_contacts -> {
                showContactsDialog()
                true
            }
            R.id.action_theme_dark -> {
                prefs.setThemeMode("dark")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                true
            }
            R.id.action_theme_light -> {
                prefs.setThemeMode("light")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                true
            }
            R.id.action_theme_system -> {
                prefs.setThemeMode("system")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPinDialog() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN transferencia (ej. 1234)"
            setText(prefs.getSavedPin())
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("PIN de transferencia")
            .setMessage("Se usará para rellenar automáticamente. Se guarda solo en este teléfono.")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.setSavedPin(et.text.toString().trim())
                Toast.makeText(this, "PIN guardado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Borrar") { _, _ ->
                prefs.setSavedPin("")
                Toast.makeText(this, "PIN borrado", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun showContactsDialog() {
        val contacts = prefs.getContacts()
        val labels = contacts.map { "${it.first} — ${it.second}" }.ifEmpty { listOf("(vacío)") }
        AlertDialog.Builder(this)
            .setTitle("Contactos frecuentes")
            .setItems(labels.toTypedArray()) { _, which ->
                if (contacts.isEmpty()) return@setItems
                val (name, number) = contacts[which]
                AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage(number)
                    .setPositiveButton("Eliminar") { _, _ ->
                        prefs.saveContacts(contacts.filterIndexed { i, _ -> i != which })
                        Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
            .setPositiveButton("Añadir") { _, _ ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 24, 48, 8)
                }
                val nameEt = EditText(this).apply { hint = "Nombre" }
                val numEt = EditText(this).apply {
                    hint = "Número"
                    inputType = InputType.TYPE_CLASS_PHONE
                }
                box.addView(nameEt)
                box.addView(numEt)
                AlertDialog.Builder(this)
                    .setTitle("Nuevo contacto")
                    .setView(box)
                    .setPositiveButton("Guardar") { _, _ ->
                        val n = nameEt.text.toString().trim()
                        val num = numEt.text.toString().trim()
                        if (num.length >= 6) {
                            prefs.addContact(n.ifBlank { num.takeLast(4) }, num)
                            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private inner class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 5
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> HomeFragment()
            1 -> ListFragment.newInstance("Consultas")
            2 -> ListFragment.newInstance("Planes")
            3 -> ListFragment.newInstance("Llamadas")
            else -> MasFragment()
        }
    }
}

// ---------- HOME ----------
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var favAdapter: UssdAdapter
    private lateinit var recentAdapter: UssdAdapter
    private val viewModel: AppViewModel by activityViewModels()
    private var query = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.root.setBackgroundColor(0x00000000) // parent provides opaque bg
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        favAdapter = makeAdapter(activity)
        recentAdapter = makeAdapter(activity)
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = favAdapter
        binding.rvRecents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecents.adapter = recentAdapter
        binding.rvFavorites.itemAnimator = null
        binding.rvRecents.itemAnimator = null

        setupShortcuts()
        viewModel.query.observe(viewLifecycleOwner) {
            query = it
            refresh()
        }
        refresh()
    }

    private fun makeAdapter(activity: MainActivity) = UssdAdapter(
        onClick = { activity.handleCodeClick(it) },
        onLongClick = { activity.copyCode(it) },
        onFavoriteClick = { activity.toggleFavorite(it) },
        isFavorite = { activity.prefs.isFavorite(it) }
    )

    private fun setupShortcuts() {
        val activity = requireActivity() as MainActivity
        val grid = binding.gridShortcuts
        grid.removeAllViews()
        val icons = listOf("💰", "📡", "📦", "🔄")
        val ids = activity.prefs.getShortcutIds()
        val codes = ids.mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .ifEmpty { CodesRepository.shortcuts }
        codes.forEachIndexed { i, code ->
            val item = layoutInflater.inflate(R.layout.item_shortcut, grid, false) as MaterialCardView
            item.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 2, 1f)
                setMargins(6, 6, 6, 6)
            }
            item.findViewById<TextView>(R.id.tvIcon).text = icons.getOrElse(i) { "☆" }
            item.findViewById<TextView>(R.id.tvLabel).text = code.title.take(12)
            item.findViewById<TextView>(R.id.tvCodeHint).text = code.code
            item.setOnClickListener { activity.handleCodeClick(code) }
            item.setOnLongClickListener {
                pickShortcut(i)
                true
            }
            grid.addView(item)
        }
    }

    private fun pickShortcut(slot: Int) {
        val activity = requireActivity() as MainActivity
        val options = CodesRepository.allCodes.map { "${it.title} (${it.code})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Elegir acceso rápido")
            .setItems(options) { _, which ->
                val ids = activity.prefs.getShortcutIds().toMutableList()
                while (ids.size < 4) ids.add("c1")
                ids[slot] = CodesRepository.allCodes[which].id
                activity.prefs.setShortcutIds(ids)
                setupShortcuts()
                Toast.makeText(requireContext(), "Acceso actualizado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    fun refresh() {
        if (_binding == null) return
        val activity = requireActivity() as MainActivity
        val favs = activity.prefs.getFavorites()
        var favList = CodesRepository.allCodes.filter { favs.contains(it.id) }
        if (query.isNotEmpty()) favList = favList.filter { CodesRepository.matchesQuery(it, query) }
        favAdapter.submitList(favList.take(6))
        binding.tvFavEmpty.isVisible = favList.isEmpty()

        var recents = activity.prefs.getRecents()
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .take(5)
        if (query.isNotEmpty()) recents = recents.filter { CodesRepository.matchesQuery(it, query) }
        recentAdapter.submitList(recents)
        binding.tvRecentsEmpty.isVisible = recents.isEmpty()
        binding.rvRecents.isVisible = recents.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---------- LIST ----------
class ListFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UssdAdapter
    private var category: String = "Consultas"
    private val viewModel: AppViewModel by activityViewModels()
    private var query = ""

    companion object {
        fun newInstance(category: String) = ListFragment().apply {
            arguments = Bundle().apply { putString("category", category) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = arguments?.getString("category") ?: "Consultas"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        adapter = UssdAdapter(
            onClick = { activity.handleCodeClick(it) },
            onLongClick = { activity.copyCode(it) },
            onFavoriteClick = { activity.toggleFavorite(it) },
            isFavorite = { activity.prefs.isFavorite(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null // prevents ghost trails
        viewModel.query.observe(viewLifecycleOwner) {
            query = it
            applyFilter()
        }
        applyFilter()
    }

    private fun baseList(): List<UssdCode> = when (category) {
        "Consultas" -> CodesRepository.allCodes.filter { it.category == "Consultas" }
        "Planes" -> CodesRepository.allCodes.filter {
            it.category == "Planes" || it.category == "Recargas"
        }
        "Llamadas" -> CodesRepository.allCodes.filter {
            it.category == "Llamadas" || it.category == "Internacional"
        }
        else -> CodesRepository.allCodes
    }

    private fun applyFilter() {
        if (_binding == null) return
        val list = baseList().filter { CodesRepository.matchesQuery(it, query) }
        adapter.submitList(list)
        binding.emptyState.isVisible = list.isEmpty()
        binding.recyclerView.isVisible = list.isNotEmpty()
        binding.tvEmpty.text = if (query.isNotEmpty())
            "Sin resultados para \"$query\"" else "Sin códigos"
    }

    fun reload() = applyFilter()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---------- MÁS (TM / Atención / Emergencias) ----------
class MasFragment : Fragment() {

    private var _binding: FragmentMasBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UssdAdapter
    private val viewModel: AppViewModel by activityViewModels()
    private var query = ""
    private var sub = "Transfermóvil"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity
        adapter = UssdAdapter(
            onClick = { activity.handleCodeClick(it) },
            onLongClick = { activity.copyCode(it) },
            onFavoriteClick = { activity.toggleFavorite(it) },
            isFavorite = { activity.prefs.isFavorite(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null

        binding.chipTm.setOnClickListener { selectSub("Transfermóvil") }
        binding.chipAtencion.setOnClickListener { selectSub("Atención") }
        binding.chipEmergencias.setOnClickListener { selectSub("Emergencias") }
        binding.chipDispositivo.setOnClickListener { selectSub("Dispositivo") }

        viewModel.query.observe(viewLifecycleOwner) {
            query = it
            applyFilter()
        }
        selectSub("Transfermóvil")
    }

    private fun selectSub(name: String) {
        sub = name
        binding.chipTm.isChecked = name == "Transfermóvil"
        binding.chipAtencion.isChecked = name == "Atención"
        binding.chipEmergencias.isChecked = name == "Emergencias"
        binding.chipDispositivo.isChecked = name == "Dispositivo"
        applyFilter()
    }

    private fun applyFilter() {
        if (_binding == null) return
        val list = CodesRepository.allCodes
            .filter { it.category == sub }
            .filter { CodesRepository.matchesQuery(it, query) }
        adapter.submitList(list)
        binding.emptyState.isVisible = list.isEmpty()
        binding.recyclerView.isVisible = list.isNotEmpty()
    }

    fun reload() = applyFilter()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
