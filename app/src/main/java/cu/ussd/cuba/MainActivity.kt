package cu.ussd.cuba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import cu.ussd.cuba.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val CALL_PERMISSION_REQUEST = 100

    private val codes = listOf(
        UssdCode("Consulta saldo principal y recursos", "*222#", "Saldo, VOZ, SMS, DATOS y vigencia de la línea"),
        UssdCode("Consulta plan de DATOS", "*222*328#", "Datos restantes y vigencia del plan"),
        UssdCode("Consulta bonos y planes en USD", "*222*266#", "Bonos promocionales y planes en dólares"),
        UssdCode("Consulta plan de VOZ", "*222*869#", "Minutos de voz disponibles y vigencia"),
        UssdCode("Consulta plan de SMS", "*222*767#", "SMS restantes y vigencia"),
        UssdCode("Estado de recargas nacionales", "*222*732#", "Cuánto puedes recargar aún (límite 360 CUP)"),
        UssdCode("Comprar / activar planes", "*133#", "Menú para planes de Datos, SMS, Voz y Plan Amigos"),
        UssdCode("Transferir saldo / Adelanta Saldo", "*234#", "Transferencia de saldo, cambio de clave o adelanto"),
        UssdCode("Recargar con tarjeta (voz)", "*666", "Sigue las instrucciones de voz"),
        UssdCode("Recargar rápido con código", "*662*CODIGO#", "Reemplaza CODIGO por el de tu tarjeta"),
        UssdCode("Consulta saldo postpago / corporativo", "*111#", "Para líneas postpago e institucionales"),
        UssdCode("Llamada a cobro revertido", "*99 + número", "El receptor paga la llamada"),
        UssdCode("Activar desvío de llamadas", "*21*número#", "Desvía todas las llamadas al número indicado"),
        UssdCode("Desactivar desvío de llamadas", "#21#", "Cancela el desvío de llamadas"),
        UssdCode("Activar llamada en espera", "*43#", "Permite recibir llamadas mientras hablas"),
        UssdCode("Desactivar llamada en espera", "#43#", "Desactiva la llamada en espera"),
        UssdCode("Llamada anónima (ocultar número)", "#31# + número", "Marca #31# seguido del número y llama"),
        UssdCode("Mostrar número (desactivar anónimo)", "*31#", "Vuelve a mostrar tu número en llamadas"),
        UssdCode("Consultar IMEI del teléfono", "*#06#", "Muestra el número IMEI del dispositivo"),
        UssdCode("Atención al cliente móvil", "52642266", "Asistencia ETECSA móvil"),
        UssdCode("Atención telefonía fija alternativa", "52642244", "Asistencia TFA"),
        UssdCode("Gestión comercial", "112", "Trámites y solicitudes"),
        UssdCode("Información de abonados", "113", "Consulta de números telefónicos"),
        UssdCode("Reparaciones telefonía fija", "114", "Reportar averías"),
        UssdCode("Emergencias - Antidrogas", "103", "Gratuito"),
        UssdCode("Emergencias - Ambulancias", "104", "Gratuito"),
        UssdCode("Emergencias - Bomberos", "105", "Gratuito"),
        UssdCode("Emergencias - Policía", "106", "Gratuito"),
        UssdCode("Emergencias - Salvamento marítimo", "107", "Gratuito")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = UssdAdapter(codes) { code ->
            dialUssd(code.code)
        }
    }

    private fun dialUssd(code: String) {
        // Limpiar código para marcar (quitar espacios y textos)
        val cleanCode = code.replace(" ", "").replace("+número", "").replace("número", "")
            .replace("CODIGO", "").trim()

        if (cleanCode.contains("número") || cleanCode.isEmpty() || code.contains("CODIGO")) {
            Toast.makeText(this, "Este código requiere que completes el número o código manualmente", Toast.LENGTH_LONG).show()
            // Abrir el marcador con el prefijo
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanCode)}")
            }
            startActivity(intent)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), CALL_PERMISSION_REQUEST)
            // Guardar temporalmente
            pendingCode = cleanCode
            return
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(cleanCode)}")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback a dialer
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanCode)}")
            }
            startActivity(dialIntent)
        }
    }

    private var pendingCode: String? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALL_PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCode?.let { dialUssd(it) }
            pendingCode = null
        } else {
            Toast.makeText(this, "Permiso de llamada denegado. Se abrirá el marcador.", Toast.LENGTH_SHORT).show()
            pendingCode?.let {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(it)}")
                }
                startActivity(intent)
            }
            pendingCode = null
        }
    }
}

data class UssdCode(
    val title: String,
    val code: String,
    val description: String
)
