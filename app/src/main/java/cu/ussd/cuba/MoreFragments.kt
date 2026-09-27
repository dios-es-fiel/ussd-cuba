package cu.ussd.cuba

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import cu.ussd.cuba.databinding.FragmentMasBinding
import cu.ussd.cuba.databinding.FragmentSettingsBinding

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
                        .setMessage(contacts[which].second)
                        .setPositiveButton("Eliminar") { _, _ ->
                            p.saveContacts(contacts.filterIndexed { i, _ -> i != which })
                            Toast.makeText(requireContext(), "Eliminado", Toast.LENGTH_SHORT).show()
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
                                p.addContact(
                                    n.text.toString().ifBlank { num.text.toString().takeLast(4) },
                                    num.text.toString()
                                )
                        }.setNegativeButton("Cancelar", null).show()
                }.setNegativeButton("Cerrar", null).show()
        }

        b.btnTemplates.setOnClickListener {
            val t = p.getTemplates()
            val labels = t.map { "${it.first}: ${it.second} / ${it.third}" }.ifEmpty { listOf("(vacío)") }
            AlertDialog.Builder(requireContext()).setTitle("Plantillas transferencia")
                .setItems(labels.toTypedArray()) { _, which ->
                    if (t.isEmpty()) return@setItems
                    val item = t[which]
                    AlertDialog.Builder(requireContext())
                        .setTitle("¿Eliminar plantilla?")
                        .setMessage("${item.first}\n${item.second} · ${item.third} CUP")
                        .setPositiveButton("Eliminar") { _, _ ->
                            p.saveTemplates(t.filterIndexed { i, _ -> i != which })
                            Toast.makeText(requireContext(), "Eliminada", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
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
                                list.add(0, Triple(
                                    n.text.toString().ifBlank { "Sin nombre" },
                                    num.text.toString(),
                                    amt.text.toString()
                                ))
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
