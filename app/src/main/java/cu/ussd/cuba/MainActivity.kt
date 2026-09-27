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
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import cu.ussd.cuba.databinding.ActivityMainBinding
import cu.ussd.cuba.databinding.FragmentHomeBinding
import cu.ussd.cuba.databinding.FragmentListBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var prefs: PrefsHelper

    private val pageTitles = listOf("Inicio", "Consultas", "Planes", "Llamadas", "Más")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Default dark theme
        when (PrefsHelper(this).getThemeMode()) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsHelper(this)
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = PagerAdapter(this)
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.offscreenPageLimit = 4

        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.nav_home -> 0
                R.id.nav_consultas -> 1
                R.id.nav_planes -> 2
                R.id.nav_llamadas -> 3
                R.id.nav_mas -> 4
                else -> 0
            }
            binding.viewPager.setCurrentItem(index, true)
            binding.toolbar.title = pageTitles[index]
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val ids = listOf(
                    R.id.nav_home, R.id.nav_consultas, R.id.nav_planes,
                    R.id.nav_llamadas, R.id.nav_mas
                )
                binding.bottomNav.selectedItemId = ids[position]
                binding.toolbar.title = pageTitles[position]
            }
        })

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                supportFragmentManager.fragments.forEach { frag ->
                    if (frag is Searchable) frag.onSearch(s?.toString() ?: "")
                }
            }
        })
    }

    fun handleCodeClick(code: UssdCode) {
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
                inputType = InputType.TYPE_CLASS_NUMBER
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
                val placeholders = Regex("\\{[^}]+\\}").findAll(code.code).map { it.value }.toList()
                placeholders.forEachIndexed { i, ph ->
                    if (i < values.size) finalCode = finalCode.replace(ph, values[i])
                }
                dial(code, finalCode)
            }
            .setNegativeButton("Cancelar", null)
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
        // Refresh home if visible
        supportFragmentManager.fragments.filterIsInstance<HomeFragment>().forEach { it.refresh() }
    }

    fun copyCode(code: UssdCode) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("USSD", code.code))
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
    }

    fun toggleFavorite(code: UssdCode) {
        val added = prefs.toggleFavorite(code.id)
        Toast.makeText(this, if (added) "Favorito ★" else "Quitado", Toast.LENGTH_SHORT).show()
        supportFragmentManager.fragments.forEach { frag ->
            when (frag) {
                is HomeFragment -> frag.refresh()
                is ListFragment -> frag.refreshFavorites()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_confirm).isChecked = prefs.getConfirmBeforeDial()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_confirm -> {
                item.isChecked = !item.isChecked
                prefs.setConfirmBeforeDial(item.isChecked)
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

    private inner class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 5
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> HomeFragment()
            1 -> ListFragment.newInstance("Consultas")
            2 -> ListFragment.newInstance("Planes")
            3 -> ListFragment.newInstance("Llamadas")
            else -> ListFragment.newInstance("Más")
        }
    }
}

interface Searchable {
    fun onSearch(query: String)
}

class HomeFragment : Fragment(), Searchable {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var favAdapter: UssdAdapter
    private lateinit var recentAdapter: UssdAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requireActivity() as MainActivity

        favAdapter = UssdAdapter(
            onClick = { activity.handleCodeClick(it) },
            onLongClick = { activity.copyCode(it) },
            onFavoriteClick = { activity.toggleFavorite(it) },
            isFavorite = { activity.prefs.isFavorite(it) }
        )
        recentAdapter = UssdAdapter(
            onClick = { activity.handleCodeClick(it) },
            onLongClick = { activity.copyCode(it) },
            onFavoriteClick = { activity.toggleFavorite(it) },
            isFavorite = { activity.prefs.isFavorite(it) }
        )

        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = favAdapter
        binding.rvRecents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecents.adapter = recentAdapter

        setupShortcuts()
        refresh()
    }

    private fun setupShortcuts() {
        val grid = binding.gridShortcuts
        grid.removeAllViews()
        val icons = listOf("💰", "📡", "📦", "🔄")
        val labels = listOf("Saldo", "Datos", "Planes", "Transferir")
        CodesRepository.shortcuts.forEachIndexed { i, code ->
            val item = layoutInflater.inflate(R.layout.item_shortcut, grid, false) as MaterialCardView
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 2, 1f)
                setMargins(6, 6, 6, 6)
            }
            item.layoutParams = params
            item.findViewById<TextView>(R.id.tvIcon).text = icons[i]
            item.findViewById<TextView>(R.id.tvLabel).text = labels[i]
            item.findViewById<TextView>(R.id.tvCodeHint).text = code.code
            item.setOnClickListener {
                (requireActivity() as MainActivity).handleCodeClick(code)
            }
            grid.addView(item)
        }
    }

    fun refresh() {
        if (_binding == null) return
        val activity = requireActivity() as MainActivity
        val favs = activity.prefs.getFavorites()
        val favList = CodesRepository.allCodes.filter { favs.contains(it.id) }
        favAdapter.submitList(favList)
        binding.tvFavEmpty.isVisible = favList.isEmpty()

        val recents = activity.prefs.getRecents()
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .take(5)
        recentAdapter.submitList(recents)
        binding.tvRecentsEmpty.isVisible = recents.isEmpty()
        binding.rvRecents.isVisible = recents.isNotEmpty()
    }

    override fun onSearch(query: String) {
        // Home doesn't filter; user can switch tab
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ListFragment : Fragment(), Searchable {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UssdAdapter
    private var category: String = "Consultas"
    private var currentQuery = ""

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
        else -> CodesRepository.allCodes.filter {
            it.category in listOf("Transfermóvil", "Atención", "Emergencias", "Dispositivo")
        }
    }

    private fun applyFilter() {
        if (_binding == null) return
        val list = baseList().filter { CodesRepository.matchesQuery(it, currentQuery) }
        adapter.submitList(list)
        binding.emptyState.isVisible = list.isEmpty()
        binding.recyclerView.isVisible = list.isNotEmpty()
        binding.tvEmpty.text = if (currentQuery.isNotEmpty())
            "Sin resultados para \"$currentQuery\"" else "Sin códigos"
    }

    fun refreshFavorites() {
        adapter.notifyDataSetChanged()
    }

    override fun onSearch(query: String) {
        currentQuery = query
        applyFilter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
