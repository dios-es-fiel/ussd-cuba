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
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import cu.ussd.cuba.databinding.FragmentSettingsBinding

// MasFragment está en MasFragment.kt

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

        val styleLabels = ThemeHelper.uiStyles.map { it.name }
        b.spinnerUiStyle.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, styleLabels)
        val styleIdx = ThemeHelper.uiStyles.indexOfFirst { it.id == p.getUiStyleId() }.coerceAtLeast(0)
        b.spinnerUiStyle.setSelection(styleIdx)
        b.tvUiStyleDesc.text = ThemeHelper.uiStyles[styleIdx].description

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
                .setMessage("Credenciales para el portal. Solo en este teléfono.")
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
            Toast.makeText(requireContext(), "Sincronizando…", Toast.LENGTH_SHORT).show()
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
                hint = "Minutos"
                setPadding(48, 32, 48, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("Contador manual")
                .setView(et)
                .setPositiveButton("Iniciar") { _, _ ->
                    val m = et.text.toString().toIntOrNull() ?: 60
                    p.setSessionDurationMin(m)
                    if (!ensureOverlayPerm()) return@setPositiveButton
                    p.setShowFloatingTime(true)
                    p.setUseNautaRealTime(false)
                    b.switchFloatTime.isChecked = true
                    OverlayService.setTime(requireContext(), m)
                }.setNegativeButton("Cancelar", null).show()
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
        b.btnSpeedConfig.setOnClickListener {
            Toast.makeText(requireContext(), "Intervalo y unidades en desarrollo", Toast.LENGTH_SHORT).show()
        }
        b.btnOverlayPerm.setOnClickListener { openOverlaySettings() }

        b.spinnerTheme.onItemSelectedListener = simpleSpinner { pos ->
            val mode = when (pos) { 1 -> "light"; 2 -> "system"; else -> "dark" }
            if (mode != p.getThemeMode()) { p.setThemeMode(mode); act.recreateWithTheme() }
        }
        b.spinnerPalette.onItemSelectedListener = simpleSpinner { pos ->
            val pid = ThemeHelper.palettes[pos].id
            if (pid != p.getPaletteId()) { p.setPaletteId(pid); act.recreateWithTheme() }
        }
        b.spinnerUiStyle.onItemSelectedListener = simpleSpinner { pos ->
            val style = ThemeHelper.uiStyles[pos]
            b.tvUiStyleDesc.text = style.description
            if (style.id != p.getUiStyleId()) {
                p.setUiStyleId(style.id)
                act.notifyStyleChanged()
                act.recreate()
            }
        }

        b.btnPin.setOnClickListener {
            val et = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setText(p.getSavedPin()); setPadding(48, 32, 48, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("PIN transferencia")
                .setView(et)
                .setPositiveButton("Guardar") { _, _ -> p.setSavedPin(et.text.toString().trim()) }
                .setNegativeButton("Borrar") { _, _ -> p.setSavedPin("") }
                .setNeutralButton("Cerrar", null).show()
        }
        b.btnContacts.setOnClickListener {
            Toast.makeText(requireContext(), "Contactos: usa el diálogo al transferir", Toast.LENGTH_SHORT).show()
        }
        b.btnTemplates.setOnClickListener {
            Toast.makeText(requireContext(), "Plantillas al marcar transferencia", Toast.LENGTH_SHORT).show()
        }
        b.btnExport.setOnClickListener {
            val json = p.exportFavoritesJson()
            val clip = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clip.setPrimaryClip(android.content.ClipData.newPlainText("favs", json))
            Toast.makeText(requireContext(), "Favoritos copiados", Toast.LENGTH_SHORT).show()
        }
        b.btnImport.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = "JSON"; minLines = 4; setPadding(48, 24, 48, 16)
            }
            AlertDialog.Builder(requireContext()).setTitle("Importar favoritos")
                .setView(et)
                .setPositiveButton("Importar") { _, _ ->
                    val n = p.importFavoriteIds(et.text.toString())
                    Toast.makeText(requireContext(), "Importados: $n", Toast.LENGTH_SHORT).show()
                    act.notifyRefresh()
                }.setNegativeButton("Cancelar", null).show()
        }
        b.btnChecklist.setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("Checklist post-recarga")
                .setMessage("1. Consultar saldo *222#\n2. Activar plan si hace falta\n3. Verificar datos *222*328#")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun simpleSpinner(onPos: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                onPos(pos)
            }
        }

    private fun pickNotifCodes(act: MainActivity) {
        val all = CodesRepository.allCodes.filter { !it.code.contains("{") }
        val labels = all.map { "${it.title} (${it.code})" }.toTypedArray()
        val checked = BooleanArray(all.size) { all[it].id in act.prefs.getNotifShortcutIds() }
        AlertDialog.Builder(requireContext()).setTitle("Códigos en notificación (máx. 4)")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Guardar") { _, _ ->
                val ids = all.filterIndexed { i, _ -> checked[i] }.map { it.id }.take(4)
                act.prefs.setNotifShortcutIds(ids)
                if (act.prefs.getShowNotifShortcuts()) QuickAccessHelper.show(requireContext(), act.prefs)
                Toast.makeText(requireContext(), "${ids.size} códigos", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun ensureOverlayPerm(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
            openOverlaySettings()
            Toast.makeText(requireContext(), "Concede permiso de ventanas flotantes", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}")))
        } catch (_: Exception) {}
    }

    private fun syncOverlays(act: MainActivity) {
        val p = act.prefs
        if (p.getShowFloatingTime() || p.getShowSpeedMonitor()) OverlayService.start(requireContext())
        else OverlayService.stop(requireContext())
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
