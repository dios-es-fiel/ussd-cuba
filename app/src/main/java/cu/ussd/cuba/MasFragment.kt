package cu.ussd.cuba

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import cu.ussd.cuba.databinding.FragmentMasBinding

class MasFragment : Fragment() {
    private var _b: FragmentMasBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: UssdAdapter
    private val vm: AppViewModel by activityViewModels()
    private var query = ""
    private var sub = "Transfermóvil"
    private var wifiBound = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMasBinding.inflate(i, c, false)
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
        b.chipTm.setOnClickListener { sub = "Transfermóvil"; sync(); apply() }
        b.chipAtencion.setOnClickListener { sub = "Atención"; sync(); apply() }
        b.chipEmergencias.setOnClickListener { sub = "Emergencias"; sync(); apply() }
        b.chipDispositivo.setOnClickListener { sub = "Dispositivo"; sync(); apply() }
        b.chipWifi.setOnClickListener { sub = "WiFi"; sync(); applyWifi() }
        vm.query.observe(viewLifecycleOwner) { query = it; if (sub == "WiFi") applyWifi() else apply() }
        vm.tick.observe(viewLifecycleOwner) { if (sub == "WiFi") refreshWifiStatus() else apply() }
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
        val act = requireActivity() as MainActivity
        val p = act.prefs
        if (!wifiBound) {
            b.etNautaUser.setText(p.getNautaUser())
            b.etNautaPass.setText(p.getNautaPass())
            b.btnNautaConnect.setOnClickListener { doConnect() }
            b.btnNautaDisconnect.setOnClickListener { doDisconnect() }
            wifiBound = true
        }
        refreshWifiStatus()
    }

    private fun refreshWifiStatus() {
        if (_b == null) return
        val p = (requireActivity() as MainActivity).prefs
        val wifi = WifiHelper.statusText(requireContext())
        val session = if (p.getNautaUuid().isNotBlank()) "Sesión activa" else "Sin sesión Nauta activa"
        b.tvWifiStatus.text = "$wifi\n$session"
    }

    private fun doConnect() {
        val act = requireActivity() as MainActivity
        val user = b.etNautaUser.text?.toString()?.trim().orEmpty()
        val pass = b.etNautaPass.text?.toString().orEmpty()
        if (user.isBlank() || pass.isBlank()) {
            Toast.makeText(requireContext(), "Escribe usuario y contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        if (!WifiHelper.isWifiConnected(requireContext())) {
            Toast.makeText(requireContext(), "Conéctate primero al Wi‑Fi (ETECSA o Nauta Hogar)", Toast.LENGTH_LONG).show()
            return
        }
        b.btnNautaConnect.isEnabled = false
        b.tvWifiStatus.text = "Conectando…"
        Thread {
            val result = NautaClient.login(user, pass)
            requireActivity().runOnUiThread {
                if (_b == null) return@runOnUiThread
                b.btnNautaConnect.isEnabled = true
                if (result.ok && result.session != null) {
                    val s = result.session!!
                    act.prefs.setNautaUser(s.username)
                    act.prefs.setNautaPass(pass)
                    act.prefs.setNautaUuid(s.attributeUuid)
                    act.prefs.setNautaCsrf(s.csrfHw)
                    act.prefs.setNautaWlanIp(s.wlanUserIp)
                    s.remainingTime?.let {
                        val secs = NautaClient.parseTimeToSeconds(it)
                        if (secs >= 0) act.prefs.setNautaRemainingSec(secs)
                    }
                    act.prefs.setNautaLastSyncMs(System.currentTimeMillis())
                }
                b.tvWifiStatus.text = "${WifiHelper.statusText(requireContext())}\n${result.message}"
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun doDisconnect() {
        val act = requireActivity() as MainActivity
        val p = act.prefs
        val user = b.etNautaUser.text?.toString()?.trim().orEmpty().ifBlank { p.getNautaUser() }
        val uuid = p.getNautaUuid()
        if (uuid.isBlank()) {
            Toast.makeText(requireContext(), "No hay sesión activa", Toast.LENGTH_SHORT).show()
            return
        }
        b.btnNautaDisconnect.isEnabled = false
        b.tvWifiStatus.text = "Desconectando…"
        Thread {
            val result = NautaClient.logout(user, uuid, p.getNautaCsrf(), p.getNautaWlanIp())
            requireActivity().runOnUiThread {
                if (_b == null) return@runOnUiThread
                b.btnNautaDisconnect.isEnabled = true
                if (result.ok) {
                    p.setNautaUuid("")
                    p.setNautaCsrf("")
                    p.setNautaWlanIp("")
                    p.setNautaRemainingSec(-1)
                }
                b.tvWifiStatus.text = "${WifiHelper.statusText(requireContext())}\n${result.message}"
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
        wifiBound = false
    }
}
