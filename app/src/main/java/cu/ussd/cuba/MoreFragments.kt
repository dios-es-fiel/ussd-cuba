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
            { act.toggleFavorite(it) }, { act.prefs.isFavorite(it) },
            { ThemeHelper.uiStyle(act.prefs.getUiStyleId()) }
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
        vm.styleTick.observe(viewLifecycleOwner) { adapter.forceRestyle() }
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
