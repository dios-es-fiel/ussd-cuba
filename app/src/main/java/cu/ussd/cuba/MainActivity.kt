package cu.ussd.cuba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import cu.ussd.cuba.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: UssdAdapter
    private val CALL_PERMISSION_REQUEST = 100
    private var pendingCode: String? = null
    private var currentCategory: String = "Todos"

    private val allCodes = listOf(
        // === CONSULTAS ===
        UssdCode("Saldo principal y recursos", "*222#", "Saldo, VOZ, SMS, DATOS y vigencia de la línea", "Consultas"),
        UssdCode("Plan de DATOS", "*222*328#", "Datos restantes y vigencia del plan", "Consultas"),
        UssdCode("Bonos y planes en USD", "*222*266#", "Bonos promocionales, datos.cu y planes en dólares", "Consultas"),
        UssdCode("Plan de VOZ", "*222*869#", "Minutos de voz disponibles y vigencia", "Consultas"),
        UssdCode("Plan de SMS", "*222*767#", "SMS restantes y vigencia", "Consultas"),
        UssdCode("Estado recargas nacionales", "*222*732#", "Cuánto puedes recargar aún (límite 360 CUP/30 días)", "Consultas"),
        UssdCode("Plan Amigos", "*222*264#", "Consulta el estado del Plan Amigos", "Consultas"),
        UssdCode("Validar internet móvil", "*222*468#", "Verifica si tu línea está habilitada para datos móviles", "Consultas"),
        UssdCode("Saldo postpago / corporativo", "*111#", "Para líneas postpago e institucionales (petroleros)", "Consultas"),
        UssdCode("Tarifa diferenciada", "*111*6#", "Recibe SMS si tienes tarifa diferenciada", "Consultas"),

        // === PLANES Y COMPRAS ===
        UssdCode("Comprar / activar planes", "*133#", "Menú: Datos, SMS, Voz, Plan Amigos y combinados", "Planes"),
        UssdCode("Transferir saldo / Adelanta", "*234#", "1-Transferencia  2-Cambiar clave  3-Adelanta Saldo", "Planes"),
        UssdCode("Transferencia directa", "*234*1*número*clave*monto#", "Transferir saldo de forma directa (clave por defecto 1234)", "Planes"),
        UssdCode("Cambiar clave transferencia", "*234*2*clave_actual*clave_nueva#", "Cambia el PIN de transferencia de saldo", "Planes"),

        // === RECARGAS ===
        UssdCode("Recargar con tarjeta (voz)", "*666", "Sigue las instrucciones de voz", "Recargas"),
        UssdCode("Recargar rápido", "*662*CODIGO#", "Reemplaza CODIGO por el de tu tarjeta de recarga", "Recargas"),

        // === LLAMADAS ===
        UssdCode("Cobro revertido (*99)", "*99", "El receptor paga la llamada. Luego marca el número", "Llamadas"),
        UssdCode("Llamada anónima", "#31#", "Oculta tu número. Luego marca el destino", "Llamadas"),
        UssdCode("Mostrar número", "*31#", "Vuelve a mostrar tu número en llamadas salientes", "Llamadas"),
        UssdCode("Activar desvío de llamadas", "*21*número#", "Desvía todas las llamadas al número indicado", "Llamadas"),
        UssdCode("Desactivar desvío", "#21#", "Cancela el desvío de llamadas", "Llamadas"),
        UssdCode("Activar llamada en espera", "*43#", "Permite recibir llamadas mientras hablas", "Llamadas"),
        UssdCode("Desactivar llamada en espera", "#43#", "Desactiva la llamada en espera", "Llamadas"),
        UssdCode("Buzón de voz", "*123", "Accede a tu buzón de voz", "Llamadas"),
        UssdCode("Buzón de voz (alternativo)", "*80", "Otra forma de acceder al buzón de voz", "Llamadas"),

        // === INTERNACIONAL ===
        UssdCode("Desactivar llamadas internacionales", "*331*clave#", "Clave inicial 0000. Protege tu saldo", "Internacional"),
        UssdCode("Activar llamadas internacionales", "#331*clave#", "Habilita el acceso internacional", "Internacional"),
        UssdCode("Cambiar clave internacional", "**03*330*clave_actual*clave_nueva*clave_nueva#", "Cambia la clave de acceso internacional", "Internacional"),

        // === DISPOSITIVO ===
        UssdCode("Consultar IMEI", "*#06#", "Muestra el número IMEI de tu teléfono", "Dispositivo"),

        // === ATENCIÓN Y EMERGENCIAS ===
        UssdCode("Atención móvil ETECSA", "52642266", "Asistencia a usuarios de telefonía móvil (24h)", "Atención"),
        UssdCode("Atención TFA", "52642244", "Asistencia Telefonía Fija Alternativa", "Atención"),
        UssdCode("Gestión comercial", "112", "Trámites y solicitudes residenciales", "Atención"),
        UssdCode("Información de abonados", "113", "Consulta de números telefónicos", "Atención"),
        UssdCode("Reparaciones fija", "114", "Reportar averías de telefonía fija", "Atención"),
        UssdCode("Atención telefónica", "2266", "Atención al cliente ETECSA", "Atención"),
        UssdCode("Emergencia - Antidrogas", "103", "Gratuito", "Emergencias"),
        UssdCode("Emergencia - Ambulancias", "104", "Gratuito", "Emergencias"),
        UssdCode("Emergencia - Bomberos", "105", "Gratuito", "Emergencias"),
        UssdCode("Emergencia - Policía", "106", "Gratuito", "Emergencias"),
        UssdCode("Emergencia - Salvamento marítimo", "107", "Gratuito", "Emergencias"),

        // === TRANSFERMÓVIL / BANCOS (USSD) ===
        UssdCode("TM - Autenticarse BANDEC", "*444*40*02#", "Iniciar sesión Transfermóvil BANDEC", "Transfermóvil"),
        UssdCode("TM - Autenticarse BANMET", "*444*40*03#", "Iniciar sesión Transfermóvil Banco Metropolitano", "Transfermóvil"),
        UssdCode("TM - Desconectar sesión", "*444*70#", "Cerrar sesión Transfermóvil", "Transfermóvil"),
        UssdCode("TM - Consultar saldo", "*444*46#", "Consulta de saldo bancario", "Transfermóvil"),
        UssdCode("TM - Últimas operaciones", "*444*48#", "Últimos movimientos de la cuenta", "Transfermóvil"),
        UssdCode("TM - Transferencia", "*444*45#", "Realizar transferencia bancaria", "Transfermóvil"),
        UssdCode("TM - Pagar electricidad", "*444*41#", "Pago de factura eléctrica", "Transfermóvil"),
        UssdCode("TM - Pagar teléfono", "*444*42#", "Pago de factura telefónica", "Transfermóvil"),
        UssdCode("TM - Pagar ONAT", "*444*43#", "Pago de impuestos ONAT", "Transfermóvil"),
        UssdCode("TM - Recarga saldo móvil", "*444*54#", "Recargar saldo Cubacel desde banco", "Transfermóvil"),
        UssdCode("TM - Recarga Nauta", "*444*59#", "Recargar cuenta Nauta", "Transfermóvil"),
        UssdCode("TM - Consulta de límites", "*444*62#", "Ver límites de operaciones", "Transfermóvil"),
        UssdCode("TM - Cambiar PIN", "*444*69#", "Cambiar el PIN de Transfermóvil", "Transfermóvil"),
        UssdCode("TM - Lista de servicios", "*444*71#", "Ver todos los servicios disponibles", "Transfermóvil")
    )

    private val categories = listOf("Todos", "Consultas", "Planes", "Recargas", "Llamadas", "Internacional", "Dispositivo", "Atención", "Emergencias", "Transfermóvil")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = UssdAdapter(allCodes) { code ->
            dialUssd(code.code)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        setupChips()
    }

    private fun setupChips() {
        binding.chipGroup.removeAllViews()
        categories.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = cat == "Todos"
                setOnClickListener {
                    currentCategory = cat
                    filterCodes(binding.searchView.query?.toString() ?: "")
                    // Uncheck others
                    for (i in 0 until binding.chipGroup.childCount) {
                        val c = binding.chipGroup.getChildAt(i) as Chip
                        c.isChecked = c.text == cat
                    }
                }
            }
            binding.chipGroup.addView(chip)
        }
    }

    private fun filterCodes(query: String) {
        val q = query.trim().lowercase()
        val filtered = allCodes.filter { code ->
            val matchCategory = currentCategory == "Todos" || code.category == currentCategory
            val matchQuery = q.isEmpty() ||
                    code.title.lowercase().contains(q) ||
                    code.code.lowercase().contains(q) ||
                    code.description.lowercase().contains(q) ||
                    code.category.lowercase().contains(q)
            matchCategory && matchQuery
        }
        adapter.updateList(filtered)
        binding.tvCount.text = "${filtered.size} códigos"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Buscar código, saldo, recarga..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterCodes(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterCodes(newText ?: "")
                return true
            }
        })
        // Keep reference for chip filtering
        binding.searchView = searchView
        return true
    }

    // Helper to keep SearchView accessible (we store it in a var via binding extension)
    private var searchViewRef: SearchView? = null
    private var ActivityMainBinding.searchView: SearchView?
        get() = searchViewRef
        set(value) { searchViewRef = value }

    private fun dialUssd(code: String) {
        val needsManual = code.contains("número", ignoreCase = true) ||
                code.contains("CODIGO") ||
                code.contains("clave") ||
                code.contains("monto")

        val cleanCode = code
            .replace(" ", "")
            .trim()

        if (needsManual) {
            Toast.makeText(this, "Completa el número/clave/monto en el marcador", Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanCode)}")
            }
            startActivity(intent)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCode = cleanCode
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST
            )
            return
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanCode)}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanCode)}")
            }
            startActivity(dialIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCode?.let { dialUssd(it) }
            } else {
                Toast.makeText(this, "Se abrirá el marcador", Toast.LENGTH_SHORT).show()
                pendingCode?.let {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${Uri.encode(it)}")
                    }
                    startActivity(intent)
                }
            }
            pendingCode = null
        }
    }
}

data class UssdCode(
    val title: String,
    val code: String,
    val description: String,
    val category: String
)
