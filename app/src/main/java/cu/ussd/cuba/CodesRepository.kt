package cu.ussd.cuba

object CodesRepository {

    val allCodes: List<UssdCode> = listOf(
        // === CONSULTAS ===
        UssdCode("c1", "Saldo principal y recursos", "*222#", "Saldo, VOZ, SMS, DATOS y vigencia de la línea", "Consultas"),
        UssdCode("c2", "Plan de DATOS", "*222*328#", "Datos restantes y vigencia del plan", "Consultas"),
        UssdCode("c3", "Bonos y planes en USD", "*222*266#", "Bonos promocionales, datos.cu y planes en dólares", "Consultas"),
        UssdCode("c4", "Plan de VOZ", "*222*869#", "Minutos de voz disponibles y vigencia", "Consultas"),
        UssdCode("c5", "Plan de SMS", "*222*767#", "SMS restantes y vigencia", "Consultas"),
        UssdCode("c6", "Estado recargas nacionales", "*222*732#", "Cuánto puedes recargar aún (límite 360 CUP/30 días)", "Consultas"),
        UssdCode("c7", "Plan Amigos", "*222*264#", "Consulta el estado del Plan Amigos", "Consultas"),
        UssdCode("c8", "Validar internet móvil", "*222*468#", "Verifica si tu línea está habilitada para datos móviles", "Consultas"),
        UssdCode("c9", "Saldo postpago / corporativo", "*111#", "Para líneas postpago e institucionales (petroleros)", "Consultas"),
        UssdCode("c10", "Tarifa diferenciada", "*111*6#", "Recibe SMS si tienes tarifa diferenciada", "Consultas"),
        UssdCode("c11", "Saldo telefonía fija", "*118#", "Consulta saldo de línea fija", "Consultas"),

        // === PLANES ===
        UssdCode("p1", "Comprar / activar planes", "*133#", "Menú: Datos, SMS, Voz, Plan Amigos y combinados", "Planes"),
        UssdCode("p2", "Transferir saldo / Adelanta", "*234#", "1-Transferencia  2-Cambiar clave  3-Adelanta Saldo", "Planes"),
        UssdCode(
            "p3", "Transferencia directa", "*234*1*{numero}*{clave}*{monto}#",
            "Transferir saldo de forma directa (clave por defecto 1234)", "Planes",
            needsParams = true, paramHints = listOf("Número destino", "Clave (1234)", "Monto CUP")
        ),
        UssdCode(
            "p4", "Cambiar clave transferencia", "*234*2*{clave_actual}*{clave_nueva}#",
            "Cambia el PIN de transferencia de saldo", "Planes",
            needsParams = true, paramHints = listOf("Clave actual", "Clave nueva")
        ),

        // === RECARGAS ===
        UssdCode("r1", "Recargar con tarjeta (voz)", "*666", "Sigue las instrucciones de voz", "Recargas"),
        UssdCode(
            "r2", "Recargar rápido", "*662*{codigo}#",
            "Introduce el código de tu tarjeta de recarga", "Recargas",
            needsParams = true, paramHints = listOf("Código de tarjeta")
        ),

        // === LLAMADAS ===
        UssdCode(
            "l1", "Cobro revertido (*99)", "*99{numero}",
            "El receptor paga la llamada", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode(
            "l2", "Llamada anónima", "#31#{numero}",
            "Oculta tu número al marcar", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l3", "Mostrar número", "*31#", "Vuelve a mostrar tu número en llamadas salientes", "Llamadas"),
        UssdCode(
            "l4", "Activar desvío de llamadas", "*21*{numero}#",
            "Desvía todas las llamadas al número indicado", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l5", "Desactivar desvío", "#21#", "Cancela el desvío de llamadas", "Llamadas"),
        UssdCode("l6", "Activar llamada en espera", "*43#", "Permite recibir llamadas mientras hablas", "Llamadas"),
        UssdCode("l7", "Desactivar llamada en espera", "#43#", "Desactiva la llamada en espera", "Llamadas"),
        UssdCode("l8", "Buzón de voz", "*123", "Accede a tu buzón de voz", "Llamadas"),
        UssdCode("l9", "Buzón de voz (alternativo)", "*80", "Otra forma de acceder al buzón de voz", "Llamadas"),

        // === INTERNACIONAL ===
        UssdCode(
            "i1", "Desactivar llamadas internacionales", "*331*{clave}#",
            "Clave inicial 0000. Protege tu saldo", "Internacional",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),
        UssdCode(
            "i2", "Activar llamadas internacionales", "#331*{clave}#",
            "Habilita el acceso internacional", "Internacional",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),
        UssdCode(
            "i3", "Cambiar clave internacional", "**03*330*{clave_actual}*{clave_nueva}*{clave_nueva}#",
            "Cambia la clave de acceso internacional", "Internacional",
            needsParams = true, paramHints = listOf("Clave actual", "Clave nueva")
        ),

        // === DISPOSITIVO ===
        UssdCode("d1", "Consultar IMEI", "*#06#", "Muestra el número IMEI de tu teléfono", "Dispositivo"),

        // === ATENCIÓN ===
        UssdCode("a1", "Atención móvil ETECSA", "52642266", "Asistencia a usuarios de telefonía móvil (24h)", "Atención"),
        UssdCode("a2", "Atención TFA", "52642244", "Asistencia Telefonía Fija Alternativa", "Atención"),
        UssdCode("a3", "Gestión comercial", "112", "Trámites y solicitudes residenciales", "Atención"),
        UssdCode("a4", "Información de abonados", "113", "Consulta de números telefónicos", "Atención"),
        UssdCode("a5", "Reparaciones fija", "114", "Reportar averías de telefonía fija", "Atención"),
        UssdCode("a6", "Atención telefónica", "2266", "Atención al cliente ETECSA", "Atención"),

        // === EMERGENCIAS ===
        UssdCode("e1", "Emergencia - Antidrogas", "103", "Gratuito", "Emergencias"),
        UssdCode("e2", "Emergencia - Ambulancias", "104", "Gratuito", "Emergencias"),
        UssdCode("e3", "Emergencia - Bomberos", "105", "Gratuito", "Emergencias"),
        UssdCode("e4", "Emergencia - Policía", "106", "Gratuito", "Emergencias"),
        UssdCode("e5", "Emergencia - Salvamento marítimo", "107", "Gratuito", "Emergencias"),

        // === TRANSFERMÓVIL ===
        UssdCode("t1", "TM - Autenticarse BANDEC", "*444*40*02#", "Iniciar sesión Transfermóvil BANDEC", "Transfermóvil"),
        UssdCode("t2", "TM - Autenticarse BANMET", "*444*40*03#", "Iniciar sesión Transfermóvil Banco Metropolitano", "Transfermóvil"),
        UssdCode("t3", "TM - Autenticarse BPA", "*444*40*01#", "Iniciar sesión Transfermóvil BPA", "Transfermóvil"),
        UssdCode("t4", "TM - Desconectar sesión", "*444*70#", "Cerrar sesión Transfermóvil", "Transfermóvil"),
        UssdCode("t5", "TM - Consultar saldo", "*444*46#", "Consulta de saldo bancario", "Transfermóvil"),
        UssdCode("t6", "TM - Últimas operaciones", "*444*48#", "Últimos movimientos de la cuenta", "Transfermóvil"),
        UssdCode("t7", "TM - Transferencia", "*444*45#", "Realizar transferencia bancaria", "Transfermóvil"),
        UssdCode("t8", "TM - Pagar electricidad", "*444*41#", "Pago de factura eléctrica", "Transfermóvil"),
        UssdCode("t9", "TM - Pagar teléfono", "*444*42#", "Pago de factura telefónica", "Transfermóvil"),
        UssdCode("t10", "TM - Pagar ONAT", "*444*43#", "Pago de impuestos ONAT", "Transfermóvil"),
        UssdCode("t11", "TM - Recarga saldo móvil", "*444*54#", "Recargar saldo Cubacel desde banco", "Transfermóvil"),
        UssdCode("t12", "TM - Recarga Nauta", "*444*59#", "Recargar cuenta Nauta", "Transfermóvil"),
        UssdCode("t13", "TM - Consulta de límites", "*444*62#", "Ver límites de operaciones", "Transfermóvil"),
        UssdCode("t14", "TM - Cambiar PIN", "*444*69#", "Cambiar el PIN de Transfermóvil", "Transfermóvil"),
        UssdCode("t15", "TM - Lista de servicios", "*444*71#", "Ver todos los servicios disponibles", "Transfermóvil"),
        UssdCode("t16", "TM - Pagar gas", "*444*67#", "Pago de gas", "Transfermóvil"),
        UssdCode("t17", "TM - Consultar todas las cuentas", "*444*58#", "Ver todas las cuentas asociadas", "Transfermóvil")
    )

    val categories = listOf(
        "Todos", "Favoritos", "Recientes", "Consultas", "Planes", "Recargas",
        "Llamadas", "Internacional", "Dispositivo", "Atención", "Emergencias", "Transfermóvil"
    )

    val shortcuts = listOf(
        allCodes.first { it.id == "c1" },
        allCodes.first { it.id == "c2" },
        allCodes.first { it.id == "p1" },
        allCodes.first { it.id == "p2" }
    )

    private val synonyms = mapOf(
        "megas" to listOf("datos", "internet", "paquete", "328"),
        "internet" to listOf("datos", "megas", "328", "468"),
        "paquete" to listOf("datos", "planes", "133"),
        "bono" to listOf("266", "promoción", "usd"),
        "minutos" to listOf("voz", "869", "llamadas"),
        "mensaje" to listOf("sms", "767"),
        "sms" to listOf("mensaje", "767"),
        "transferir" to listOf("234", "saldo", "transferencia"),
        "recarga" to listOf("666", "662", "732"),
        "saldo" to listOf("222", "consulta"),
        "imei" to listOf("06", "dispositivo"),
        "banco" to listOf("444", "transfermóvil", "tm"),
        "emergencia" to listOf("103", "104", "105", "106", "107")
    )

    fun matchesQuery(code: UssdCode, query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase().trim()
        if (code.title.lowercase().contains(q)) return true
        if (code.code.lowercase().contains(q)) return true
        if (code.description.lowercase().contains(q)) return true
        if (code.category.lowercase().contains(q)) return true
        synonyms[q]?.forEach { syn ->
            if (code.title.lowercase().contains(syn) ||
                code.code.lowercase().contains(syn) ||
                code.description.lowercase().contains(syn)
            ) return true
        }
        // partial synonym match
        synonyms.forEach { (key, values) ->
            if (key.contains(q) || q.contains(key)) {
                values.forEach { syn ->
                    if (code.title.lowercase().contains(syn) ||
                        code.code.lowercase().contains(syn)
                    ) return true
                }
            }
        }
        return false
    }
}
