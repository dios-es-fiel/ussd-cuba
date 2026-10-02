package cu.ussd.cuba

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import cu.ussd.cuba.databinding.FragmentHomeBinding
import cu.ussd.cuba.databinding.FragmentListBinding

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private lateinit var listAdapter: UssdAdapter
    private val vm: AppViewModel by activityViewModels()
    private var query = ""
    /** 0=Favoritos 1=Más usados 2=Recientes */
    private var homeType = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = requireActivity() as MainActivity
        listAdapter = ad(act)
        b.rvHomeList.layoutManager = LinearLayoutManager(requireContext())
        b.rvHomeList.adapter = listAdapter
        b.rvHomeList.itemAnimator = null
        applyListPadding(act)

        b.btnEmergency.setOnClickListener { act.showEmergencyPanel() }
        b.tvSeeAllFav.setOnClickListener { act.showAllFavorites() }

        b.chipFav.setOnClickListener { homeType = 0; refresh() }
        b.chipUsed.setOnClickListener { homeType = 1; refresh() }
        b.chipRecent.setOnClickListener { homeType = 2; refresh() }

        setupShortcuts()
        vm.query.observe(viewLifecycleOwner) { query = it; refresh() }
        vm.tick.observe(viewLifecycleOwner) { refresh() }
        vm.styleTick.observe(viewLifecycleOwner) {
            applyListPadding(act)
            setupShortcuts()
            listAdapter.forceRestyle()
        }
        refresh()
    }

    private fun ad(act: MainActivity) = UssdAdapter(
        { act.handleCodeClick(it) }, { act.copyCode(it) },
        { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) },
        { ThemeHelper.uiStyle(act.prefs.getUiStyleId()) }
    )

    private fun applyListPadding(act: MainActivity) {
        if (_b == null) return
        val style = ThemeHelper.uiStyle(act.prefs.getUiStyleId())
        val h = (style.listPaddingHDp * resources.displayMetrics.density).toInt()
        b.rvHomeList.setPadding(h, b.rvHomeList.paddingTop, h, b.rvHomeList.paddingBottom)
        b.rvHomeList.clipToPadding = false
    }

    private fun setupShortcuts() {
        if (_b == null) return
        val act = requireActivity() as MainActivity
        val grid = b.gridShortcuts
        grid.removeAllViews()
        val style = ThemeHelper.uiStyle(act.prefs.getUiStyleId())
        val density = resources.displayMetrics.density
        val icons = listOf("💰", "📡", "📦", "🔄")
        val codes = act.prefs.getShortcutIds()
            .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
            .ifEmpty { CodesRepository.shortcuts }
        codes.forEachIndexed { i, code ->
            val item = layoutInflater.inflate(R.layout.item_shortcut, grid, false) as MaterialCardView
            item.radius = style.cornerRadiusDp * density
            item.cardElevation = style.cardElevationDp * density
            item.useCompatPadding = style.cardElevationDp > 0f
            item.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 2, 1f)
                rowSpec = GridLayout.spec(i / 2)
                val m = (4 * density).toInt()
                setMargins(m, m, m, m)
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

        b.chipFav.isChecked = homeType == 0
        b.chipUsed.isChecked = homeType == 1
        b.chipRecent.isChecked = homeType == 2

        val list: List<UssdCode>
        when (homeType) {
            1 -> {
                b.tvSectionTitle.text = "Más usados"
                b.tvSeeAllFav.isVisible = false
                var most = act.prefs.getMostUsed(8)
                    .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }
                if (query.isNotEmpty()) most = most.filter { CodesRepository.matchesQuery(it, query) }
                list = most
                b.tvListEmpty.text = "Se llenará al marcar códigos"
            }
            2 -> {
                b.tvSectionTitle.text = "Recientes"
                b.tvSeeAllFav.isVisible = false
                var rec = act.prefs.getRecents()
                    .mapNotNull { id -> CodesRepository.allCodes.find { it.id == id } }.take(8)
                if (query.isNotEmpty()) rec = rec.filter { CodesRepository.matchesQuery(it, query) }
                list = rec
                b.tvListEmpty.text = "Aún no has marcado códigos"
            }
            else -> {
                b.tvSectionTitle.text = "Favoritos"
                val favs = act.prefs.getFavorites()
                var favList = CodesRepository.allCodes.filter { it.id in favs }
                if (query.isNotEmpty()) favList = favList.filter { CodesRepository.matchesQuery(it, query) }
                list = favList.take(8)
                b.tvSeeAllFav.isVisible = favList.size > 8 || favs.size > 8
                b.tvListEmpty.text = "Marca con ★ cualquier código"
            }
        }

        listAdapter.submitList(list)
        b.tvListEmpty.isVisible = list.isEmpty()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class ListFragment : Fragment() {
    private var _b: FragmentListBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: UssdAdapter
    private var module = "Consultas"
    private var subtypeId = "todo"
    private val vm: AppViewModel by activityViewModels()
    private var query = ""

    companion object {
        fun newInstance(mod: String) = ListFragment().apply {
            arguments = Bundle().apply { putString("module", mod) }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        module = arguments?.getString("module") ?: "Consultas"
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentListBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val act = requireActivity() as MainActivity
        adapter = UssdAdapter(
            { act.handleCodeClick(it) }, { act.copyCode(it) },
            { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) },
            { ThemeHelper.uiStyle(act.prefs.getUiStyleId()) }
        )
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        b.recyclerView.itemAnimator = null
        applyListPadding(act)
        setupSubtypeChips()
        vm.query.observe(viewLifecycleOwner) { query = it; apply() }
        vm.tick.observe(viewLifecycleOwner) { apply() }
        vm.styleTick.observe(viewLifecycleOwner) {
            applyListPadding(act)
            adapter.forceRestyle()
        }
        apply()
    }

    private fun setupSubtypeChips() {
        val group = b.chipGroupSubtypes
        group.removeAllViews()
        val subtypes = ModuleStructure.subtypesFor(module)
        if (subtypes.isEmpty()) {
            b.subtypeScroll.isVisible = false
            return
        }
        b.subtypeScroll.isVisible = true
        subtypes.forEachIndexed { index, st ->
            val chip = Chip(requireContext()).apply {
                text = st.label
                isCheckable = true
                isChecked = index == 0
                setOnClickListener {
                    subtypeId = st.id
                    // marcar solo este chip
                    for (i in 0 until group.childCount) {
                        (group.getChildAt(i) as? Chip)?.isChecked = group.getChildAt(i) === this
                    }
                    apply()
                }
            }
            group.addView(chip)
        }
        subtypeId = subtypes.first().id
    }

    private fun applyListPadding(act: MainActivity) {
        if (_b == null) return
        val style = ThemeHelper.uiStyle(act.prefs.getUiStyleId())
        val h = (style.listPaddingHDp * resources.displayMetrics.density).toInt()
        b.recyclerView.setPadding(h, b.recyclerView.paddingTop, h, b.recyclerView.paddingBottom)
        b.recyclerView.clipToPadding = false
    }

    private fun apply() {
        if (_b == null) return
        val subtypes = ModuleStructure.subtypesFor(module)
        val filter = subtypes.find { it.id == subtypeId }?.filter
            ?: { c: UssdCode -> c.category == module }

        val list = CodesRepository.allCodes
            .filter(filter)
            .filter { CodesRepository.matchesQuery(it, query) }

        adapter.submitList(list)
        b.emptyState.isVisible = list.isEmpty()
        b.recyclerView.isVisible = list.isNotEmpty()
        b.tvEmpty.text = if (query.isNotEmpty()) "Sin resultados" else "Sin códigos en este tipo"

        val label = subtypes.find { it.id == subtypeId }?.label ?: module
        b.tvModuleHint.text = "$module  ·  $label  (${list.size})"
        b.tvModuleHint.isVisible = true
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
