package cu.ussd.cuba

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
        b.chipWifi.setOnClickListener { sub = "WiFi"; sync(); applyWifi() }
        vm.query.observe(viewLifecycleOwner) { query = it; if (sub == "WiFi") applyWifi() else apply() }
        vm.tick.observe(viewLifecycleOwner) { if (sub == "WiFi") applyWifi() else apply() }
        sync(); apply()
    }

    private fun sync() {
        b.chipTm.isChecked = sub == "Transfermóvil"
        b.chipAtencion.isChecked = sub == "Atención"
        b.chipEmergencias.isChecked = sub == "Emergencias"
        b.chipDispositivo.isChecked = sub == "Dispositivo"
        b.chipWifi.isChecked = sub == "WiFi"
        b.wifiPanel.isVisible = sub == "WiFi"
        b.recyclerView.isVisible = sub != "WiFi"
        b.emptyState.isVisible = false
    }

    private fun apply() {
        if (_b == null || sub == "WiFi") return
        val list = CodesRepository.allCodes.filter { it.category == sub }
            .filter { CodesRepository.matchesQuery(it, query) }
        adapter.submitList(list)
        b.emptyState.isVisible = list.isEmpty()
        b.recyclerView.isVisible = list.isNotEmpty()
    }

    private fun applyWifi() {
        if (_b == null) return
        b.recyclerView.isVisible = false
        b.emptyState.isVisible = false
        b.wifiPanel.isVisible = true
        b.tvWifiStatus.text = WifiHelper.statusText(requireContext())
        b.btnOpenPortal.setOnClickListener { WifiHelper.openPortal(requireContext(), 0) }
        b.btnOpenPortalAlt.setOnClickListener { WifiHelper.openPortal(requireContext(), 1) }
        b.btnNautaPortal.setOnClickListener { WifiHelper.openPortal(requireContext(), 2) }
        b.btnWifiSettings.setOnClickListener { WifiHelper.openWifiSettings(requireContext()) }
        b.btnStartSession.setOnClickListener {
            val act = requireActivity() as MainActivity
            if (act.prefs.getNautaUser().isNotBlank()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(requireContext())
                ) {
                    Toast.makeText(requireContext(), "Concede permiso de ventanas flotantes", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${requireContext().packageName}")
                        ))
                    } catch (_: Exception) {}
                    return@setOnClickListener
                }
                act.prefs.setShowFloatingTime(true)
                act.prefs.setUseNautaRealTime(true)
                OverlayService.syncNauta(requireContext())
                Toast.makeText(requireContext(), "Sincronizando tiempo Nauta…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val options = arrayOf("15 min", "30 min", "60 min", "90 min", "120 min", "Personalizado")
            AlertDialog.Builder(requireContext())
                .setTitle("Iniciar contador (sin cuenta Nauta guardada)")
                .setItems(options) { _, which ->
                    val m = when (which) {
                        0 -> 15; 1 -> 30; 2 -> 60; 3 -> 90; 4 -> 120
                        else -> {
                            showCustomMinutes(act)
                            return@setItems
                        }
                    }
                    startFloatTime(act, m)
                }.setNegativeButton("Cerrar", null).show()
        }
    }

    private fun showCustomMinutes(act: MainActivity) {
        val et = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Minutos"
            setText(act.prefs.getSessionDurationMin().toString())
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext()).setTitle("Duración (minutos)")
            .setView(et)
            .setPositiveButton("Iniciar") { _, _ ->
                val m = et.text.toString().toIntOrNull() ?: 60
                startFloatTime(act, m)
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun startFloatTime(act: MainActivity, minutes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(requireContext())
        ) {
            Toast.makeText(requireContext(), "Concede permiso de ventanas flotantes en Ajustes", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                ))
            } catch (_: Exception) {}
            return
        }
        act.prefs.setShowFloatingTime(true)
        act.prefs.setUseNautaRealTime(false)
        OverlayService.setTime(requireContext(), minutes)
        Toast.makeText(requireContext(), "Contador manual: $minutes min", Toast.LENGTH_SHORT).show()
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

        b.switchNotifShortcuts.isChecked = p.getShowNotifShortcuts()
        b.switchNotifShortcuts.setOnCheckedChangeListener { _, v ->
            p.setShowNotifShortcuts(v)
            if (v) QuickAccessHelper.show(requireContext(), p)
            else QuickAccessHelper.cancel(requireContext())
        }
        b.btnNotifCodes.setOnClickListener { pickNotifCodes(act) }

        b.switchNautaReal.isChecked = p.getUseNautaRealTime()
        b.switchNautaReal.setOnCheckedChangeListener { _, v ->
            p.setUseNautaRealTime(v)
            if (v && p.getShowFloatingTime()) OverlayService.syncNauta(requireContext())
        }
        b.btnNautaAccount.setOnClickListener {
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
            }
            val user = EditText(requireContext()).apply {
                hint = "usuario@nauta.com.cu"
                setText(p.getNautaUser())
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            val pass = EditText(requireContext()).apply {
                hint = "Contraseña"
                setText(p.getNautaPass())
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            box.addView(user); box.addView(pass)
            AlertDialog.Builder(requireContext())
                .setTitle("Cuenta Nauta")
                .setMessage("Se usa para consultar el tiempo restante real en el portal ETECSA. Solo en este teléfono.")
                .setView(box)
                .setPositiveButton("Guardar") { _, _ ->
                    p.setNautaUser(user.text.toString())
                    p.setNautaPass(pass.text.toString())
                    Toast.makeText(requireContext(), "Cuenta guardada", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Borrar") { _, _ ->
                    p.setNautaUser(""); p.setNautaPass(""); p.setNautaUuid("")
                    Toast.makeText(requireContext(), "Cuenta borrada", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Cerrar", null).show()
        }
        b.btnSyncNauta.setOnClickListener {
            if (p.getNautaUser().isBlank()) {
                Toast.makeText(requireContext(), "Guarda primero la cuenta Nauta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!ensureOverlayPerm()) return@setOnClickListener
            p.setShowFloatingTime(true)
            p.setUseNautaRealTime(true)
            b.switchFloatTime.isChecked = true
            b.switchNautaReal.isChecked = true
            OverlayService.syncNauta(requireContext())
            Toast.makeText(requireContext(), "Sincronizando con portal ETECSA…", Toast.LENGTH_SHORT).show()
        }

        b.switchFloatTime.isChecked = p.getShowFloatingTime()
        b.switchFloatTime.setOnCheckedChangeListener { _, v ->
            if (v && !ensureOverlayPerm()) {
                b.switchFloatTime.isChecked = false
                return@setOnCheckedChangeListener
            }
            p.setShowFloatingTime(v)
            syncOverlays(act)
            if (v && p.getUseNautaRealTime()) OverlayService.syncNauta(requireContext())
        }
        b.btnSetSessionTime.setOnClickListener {
            val et = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(p.getSessionDurationMin().toString())
                hint = "Minutos de sesión"
                setPadding(48, 32, 48, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("Contador manual")
                .setMessage("Solo si no usas tiempo real Nauta")
                .setView(et)
                .setPositiveButton("Guardar e iniciar") { _, _ ->
                    val m = et.text.toString().toIntOrNull() ?: 60
                    p.setSessionDurationMin(m)
                    if (!ensureOverlayPerm()) return@setPositiveButton
                    p.setShowFloatingTime(true)
                    p.setUseNautaRealTime(false)
                    b.switchFloatTime.isChecked = true
                    b.switchNautaReal.isChecked = false
                    OverlayService.setTime(requireContext(), m)
                    Toast.makeText(requireContext(), "$m min iniciados", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Solo guardar") { _, _ ->
                    val m = et.text.toString().toIntOrNull() ?: 60
                    p.setSessionDurationMin(m)
                }.setNeutralButton("Cerrar", null).show()
        }

        b.switchSpeed.isChecked = p.getShowSpeedMonitor()
        b.switchSpeed.setOnCheckedChangeListener { _, v ->
            if (v && !ensureOverlayPerm()) {
                b.switchSpeed.isChecked = false
                return@setOnCheckedChangeListener
            }
            p.setShowSpeedMonitor(v)
            syncOverlays(act)
        }
        b.btnSpeedConfig.setOnClickListener { showSpeedConfig(act) }
        b.btnOverlayPerm.setOnClickListener { openOverlaySettings() }

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

    private fun ensureOverlayPerm(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(requireContext())
        ) {
            Toast.makeText(requireContext(), "Activa «Mostrar sobre otras apps»", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return false
        }
        return true
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")
            ))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Abre Ajustes → Apps → USSD Cuba → Ventanas flotantes", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncOverlays(act: MainActivity) {
        val p = act.prefs
        if (p.getShowFloatingTime() || p.getShowSpeedMonitor()) {
            OverlayService.start(requireContext())
            OverlayService.refresh(requireContext())
        } else {
            OverlayService.stop(requireContext())
        }
    }

    private fun pickNotifCodes(act: MainActivity) {
        val all = CodesRepository.allCodes.filter { !it.needsParams }
        val labels = all.map { "${it.title}  ${it.code}" }.toTypedArray()
        val current = act.prefs.getNotifShortcutIds().toMutableSet()
        val checked = BooleanArray(all.size) { i -> all[i].id in current }
        AlertDialog.Builder(requireContext())
            .setTitle("Códigos en notificación (máx. 4)")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Guardar") { _, _ ->
                val ids = mutableListOf<String>()
                checked.forEachIndexed { i, on -> if (on) ids.add(all[i].id) }
                act.prefs.setNotifShortcutIds(ids.take(4))
                if (act.prefs.getShowNotifShortcuts()) {
                    QuickAccessHelper.show(requireContext(), act.prefs)
                }
                Toast.makeText(requireContext(), "${ids.take(4).size} códigos", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSpeedConfig(act: MainActivity) {
        val p = act.prefs
        val items = arrayOf(
            "Unidad: ${when (p.getSpeedUnit()) { 0 -> "KB/s"; 1 -> "MB/s"; else -> "Auto" }}",
            "Intervalo: ${p.getSpeedIntervalMs()} ms",
            "Mostrar subida: ${if (p.getSpeedShowUpload()) "Sí" else "No"}",
            "Opacidad overlay: ${p.getOverlayOpacity()}",
            "Tamaño texto: ${p.getOverlayTextSizeSp()} sp",
            "Restablecer posición overlay"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Monitor de velocidad")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val units = arrayOf("KB/s", "MB/s", "Auto")
                        AlertDialog.Builder(requireContext()).setTitle("Unidad")
                            .setItems(units) { _, u ->
                                p.setSpeedUnit(u)
                                OverlayService.refresh(requireContext())
                            }.show()
                    }
                    1 -> {
                        val opts = arrayOf("500 ms", "1000 ms", "2000 ms", "3000 ms")
                        AlertDialog.Builder(requireContext()).setTitle("Intervalo de actualización")
                            .setItems(opts) { _, i ->
                                p.setSpeedIntervalMs(listOf(500, 1000, 2000, 3000)[i])
                                OverlayService.refresh(requireContext())
                            }.show()
                    }
                    2 -> {
                        p.setSpeedShowUpload(!p.getSpeedShowUpload())
                        OverlayService.refresh(requireContext())
                        Toast.makeText(requireContext(),
                            if (p.getSpeedShowUpload()) "Subida visible" else "Solo bajada",
                            Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val opts = arrayOf("Baja (120)", "Media (180)", "Alta (220)", "Máxima (255)")
                        AlertDialog.Builder(requireContext()).setTitle("Opacidad")
                            .setItems(opts) { _, i ->
                                p.setOverlayOpacity(listOf(120, 180, 220, 255)[i])
                                OverlayService.refresh(requireContext())
                            }.show()
                    }
                    4 -> {
                        val opts = arrayOf("10", "12", "14", "16", "18", "20")
                        AlertDialog.Builder(requireContext()).setTitle("Tamaño texto (sp)")
                            .setItems(opts) { _, i ->
                                p.setOverlayTextSizeSp(listOf(10, 12, 14, 16, 18, 20)[i])
                                OverlayService.stop(requireContext())
                                if (p.getShowSpeedMonitor() || p.getShowFloatingTime())
                                    OverlayService.start(requireContext())
                            }.show()
                    }
                    5 -> {
                        p.setOverlayX(40)
                        p.setOverlayY(200)
                        OverlayService.stop(requireContext())
                        if (p.getShowSpeedMonitor() || p.getShowFloatingTime())
                            OverlayService.start(requireContext())
                        Toast.makeText(requireContext(), "Posición restablecida", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
