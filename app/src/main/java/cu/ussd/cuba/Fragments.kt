package cu.ussd.cuba

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import cu.ussd.cuba.databinding.FragmentHomeBinding
import cu.ussd.cuba.databinding.FragmentListBinding
import cu.ussd.cuba.databinding.FragmentMasBinding
import cu.ussd.cuba.databinding.FragmentSettingsBinding

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
