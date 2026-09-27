package cu.ussd.cuba

object CodesRepository {

    val allCodes: List<UssdCode> = listOf(
        // === CONSULTAS (UtilEs + ETECSA oficial 2025) ===
        UssdCode("c1", "Saldo principal y recursos", "*222#",
            "Saldo CUP, VOZ, SMS, DATOS y vigencia de la línea", "Consultas"),
        UssdCode("c2", "Plan de DATOS", "*222*328#",
            "Megas del paquete activo y fecha de vencimiento", "Consultas"),
        UssdCode("c3", "Bonos y planes en USD", "*222*266#",
            "Bonos promo, datos.cu, LTE y planes en dólares", "Consultas"),
        UssdCode("c4", "Plan de VOZ", "*222*869#",
            "Minutos de voz restantes y vigencia", "Consultas"),
        UssdCode("c5", "Plan de SMS", "*222*767#",
            "SMS restantes y vigencia", "Consultas"),
        UssdCode("c6", "Límite recargas nacionales", "*222*732#",
            "Cuánto puedes recargar aún (tope 360 CUP / 30 días)", "Consultas"),
        UssdCode("c7", "Plan Amigos", "*222*264#",
            "Estado e inscripciones del Plan Amigos", "Consultas"),
        UssdCode("c8", "Validar internet móvil", "*222*468#",
            "Comprueba si la línea tiene datos móviles habilitados", "Consultas"),
        UssdCode("c9", "Saldo postpago / corporativo", "*111#",
            "Líneas postpago e institucionales (petroleros)", "Consultas"),
        UssdCode("c10", "Tarifa diferenciada", "*111*6#",
            "Recibe SMS si tienes tarifa diferenciada", "Consultas"),
        UssdCode("c11", "Saldo telefonía fija", "*118#",
            "Consulta saldo de línea fija", "Consultas"),
        UssdCode("c12", "Estado desvío incondicional", "*#21#",
            "Consulta si tienes desvío de todas las llamadas", "Consultas"),
        UssdCode("c13", "Estado llamada en espera", "*#43#",
            "Consulta si la llamada en espera está activa", "Consultas"),
        UssdCode("c14", "Estado ocultar número (CLIR)", "*#31#",
            "Consulta si tu número se oculta al marcar", "Consultas"),

        // === PLANES (menú *133# estilo UtilEs/QvaCall) ===
        UssdCode("p1", "Menú comprar planes", "*133#",
            "1-Datos  2-SMS  3-Voz  4-Plan Amigos  5-Combinados", "Planes"),
        UssdCode("p1d", "Comprar plan DATOS", "*133*1#",
            "Acceso directo al menú de paquetes de datos", "Planes"),
        UssdCode("p1s", "Comprar plan SMS", "*133*2#",
            "Acceso directo a planes de mensajes", "Planes"),
        UssdCode("p1v", "Comprar plan VOZ", "*133*3#",
            "Acceso directo a planes de minutos", "Planes"),
        UssdCode("p1a", "Plan Amigos (menú)", "*133*4#",
            "Inscribir, consultar o gestionar Plan Amigos", "Planes"),
        UssdCode("p1c", "Planes combinados", "*133*5#",
            "Datos + Voz + SMS en un solo plan", "Planes"),
        UssdCode("p2", "Menú transferir / Adelanta", "*234#",
            "1-Transferencia  2-Cambiar clave  3-Adelanta Saldo", "Planes"),
        UssdCode(
            "p3", "Transferencia directa", "*234*1*{numero}*{clave}*{monto}#",
            "Transferir saldo (PIN por defecto 1234). Comisión ~5 CUP", "Planes",
            needsParams = true, paramHints = listOf("Número destino", "Clave (1234)", "Monto CUP")
        ),
        UssdCode(
            "p4", "Cambiar clave transferencia", "*234*2*{clave_actual}*{clave_nueva}#",
            "Cambia el PIN de transferencia de saldo", "Planes",
            needsParams = true, paramHints = listOf("Clave actual", "Clave nueva")
        ),
        UssdCode("p5", "Adelanta Saldo", "*234*3#",
            "Solicitar adelanto de saldo a ETECSA", "Planes"),

        // === RECARGAS ===
        UssdCode("r1", "Recargar con tarjeta (voz)", "*666",
            "Sigue las instrucciones de voz del sistema", "Recargas"),
        UssdCode(
            "r2", "Recargar rápido (código)", "*662*{codigo}#",
            "Introduce los dígitos de la tarjeta de recarga", "Recargas",
            needsParams = true, paramHints = listOf("Código de tarjeta")
        ),

        // === LLAMADAS (UtilEs + QvaCall) ===
        UssdCode(
            "l1", "Cobro revertido (*99)", "*99{numero}",
            "El receptor paga la llamada — útil sin saldo", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode(
            "l2", "Llamada anónima", "#31#{numero}",
            "Oculta tu número en esta llamada", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l3", "Mostrar número (activar)", "*31#",
            "Muestra tu número en llamadas salientes", "Llamadas"),
        UssdCode("l3b", "Ocultar número (activar)", "#31#",
            "Oculta tu número en todas las llamadas salientes", "Llamadas"),
        UssdCode(
            "l4", "Desvío todas las llamadas", "*21*{numero}#",
            "Desvía TODAS las llamadas al número indicado", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l5", "Cancelar desvío total", "#21#",
            "Cancela el desvío incondicional", "Llamadas"),
        UssdCode(
            "l4b", "Desvío si ocupado", "*67*{numero}#",
            "Desvía solo cuando estás en otra llamada", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l5b", "Cancelar desvío ocupado", "#67#",
            "Cancela desvío por ocupado", "Llamadas"),
        UssdCode(
            "l4c", "Desvío si no contestas", "*61*{numero}#",
            "Desvía si no respondes", "Llamadas",
            needsParams = true, paramHints = listOf("Número destino")
        ),
        UssdCode("l5c", "Cancelar desvío no contesta", "#61#",
            "Cancela desvío por no contestar", "Llamadas"),
        UssdCode("l5d", "Cancelar TODOS los desvíos", "##002#",
            "Elimina todos los desvíos activos", "Llamadas"),
        UssdCode("l6", "Activar llamada en espera", "*43#",
            "Recibe otra llamada mientras hablas", "Llamadas"),
        UssdCode("l7", "Desactivar llamada en espera", "#43#",
            "Desactiva la llamada en espera", "Llamadas"),
        UssdCode("l8", "Buzón de voz", "*123",
            "Accede a tu buzón de voz Cubacel", "Llamadas"),
        UssdCode("l9", "Buzón de voz (alt)", "*80",
            "Acceso alternativo al buzón de voz", "Llamadas"),
        UssdCode(
            "l10", "Restringir llamadas salientes", "*33*{clave}#",
            "Bloquea llamadas salientes (clave típica 0000)", "Llamadas",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),
        UssdCode(
            "l11", "Quitar restricción salientes", "#33*{clave}#",
            "Quita el bloqueo de llamadas salientes", "Llamadas",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),

        // === INTERNACIONAL ===
        UssdCode(
            "i1", "Bloquear llamadas internacionales", "*331*{clave}#",
            "Clave inicial 0000 — protege tu saldo", "Internacional",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),
        UssdCode(
            "i2", "Activar llamadas internacionales", "#331*{clave}#",
            "Habilita marcar al extranjero", "Internacional",
            needsParams = true, paramHints = listOf("Clave (0000)")
        ),
        UssdCode(
            "i3", "Cambiar clave internacional", "**03*330*{clave_actual}*{clave_nueva}*{clave_nueva}#",
            "Cambia la clave de restricción internacional", "Internacional",
            needsParams = true, paramHints = listOf("Clave actual", "Clave nueva")
        ),

        // === DISPOSITIVO ===
        UssdCode("d1", "Consultar IMEI", "*#06#",
            "Muestra el IMEI del teléfono (útil para reporte de robo)", "Dispositivo"),
        UssdCode("d2", "Info teléfono (menú prueba)", "*#*#4636#*#*",
            "Menú oculto: batería, uso, Wi‑Fi, red (varía por marca)", "Dispositivo"),

        // === ATENCIÓN ===
        UssdCode("a1", "Atención móvil ETECSA", "52642266",
            "Asistencia telefonía móvil 24 h", "Atención"),
        UssdCode("a2", "Atención TFA", "52642244",
            "Telefonía Fija Alternativa", "Atención"),
        UssdCode("a3", "Gestión comercial", "112",
            "Trámites residenciales sin ir a oficina", "Atención"),
        UssdCode("a4", "Información de abonados", "113",
            "Consulta de números telefónicos públicos", "Atención"),
        UssdCode("a5", "Reparaciones fija", "114",
            "Reportar averías de telefonía fija", "Atención"),
        UssdCode("a6", "Atención al cliente", "2266",
            "Atención general ETECSA", "Atención"),
        UssdCode("a7", "Línea ayuda al menor", "116111",
            "Ayuda a menores de edad", "Atención"),

        // === EMERGENCIAS ===
        UssdCode("e1", "Antidrogas", "103", "Gratuito", "Emergencias"),
        UssdCode("e2", "Ambulancias", "104", "Gratuito", "Emergencias"),
        UssdCode("e3", "Bomberos", "105", "Gratuito", "Emergencias"),
        UssdCode("e4", "Policía", "106", "Gratuito", "Emergencias"),
        UssdCode("e5", "Salvamento marítimo", "107", "Gratuito", "Emergencias"),

        // === TRANSFERMÓVIL (completo) ===
        UssdCode("t1", "TM · Login BANDEC", "*444*40*02#",
            "Autenticarse Banco de Crédito y Comercio", "Transfermóvil"),
        UssdCode("t2", "TM · Login BANMET", "*444*40*03#",
            "Autenticarse Banco Metropolitano", "Transfermóvil"),
        UssdCode("t3", "TM · Login BPA", "*444*40*01#",
            "Autenticarse Banco Popular de Ahorro", "Transfermóvil"),
        UssdCode("t4", "TM · Cerrar sesión", "*444*70#",
            "Desconectar sesión Transfermóvil", "Transfermóvil"),
        UssdCode("t5", "TM · Saldo bancario", "*444*46#",
            "Consultar saldo de la cuenta", "Transfermóvil"),
        UssdCode("t6", "TM · Últimas operaciones", "*444*48#",
            "Últimos movimientos", "Transfermóvil"),
        UssdCode("t7", "TM · Transferencia", "*444*45#",
            "Transferir entre cuentas", "Transfermóvil"),
        UssdCode("t8", "TM · Pagar electricidad", "*444*41#",
            "Pago de factura eléctrica", "Transfermóvil"),
        UssdCode("t9", "TM · Pagar teléfono", "*444*42#",
            "Pago de factura telefónica", "Transfermóvil"),
        UssdCode("t10", "TM · Pagar ONAT", "*444*43#",
            "Pago de impuestos", "Transfermóvil"),
        UssdCode("t10b", "TM · Pagar multa", "*444*44#",
            "Pago de multas", "Transfermóvil"),
        UssdCode("t11", "TM · Recarga móvil", "*444*54#",
            "Recargar Cubacel desde el banco", "Transfermóvil"),
        UssdCode("t12", "TM · Recarga Nauta", "*444*59#",
            "Recargar cuenta Nauta", "Transfermóvil"),
        UssdCode("t13", "TM · Límites", "*444*62#",
            "Consultar límites de operaciones", "Transfermóvil"),
        UssdCode("t14", "TM · Cambiar PIN", "*444*69#",
            "Cambiar PIN de Transfermóvil", "Transfermóvil"),
        UssdCode("t15", "TM · Lista de servicios", "*444*71#",
            "Ver servicios disponibles", "Transfermóvil"),
        UssdCode("t16", "TM · Pagar gas", "*444*67#",
            "Pago de gas", "Transfermóvil"),
        UssdCode("t17", "TM · Todas las cuentas", "*444*58#",
            "Consulta integrada de cuentas", "Transfermóvil"),
        UssdCode("t18", "TM · Consultar servicios", "*444*47#",
            "Servicios asociados a la cuenta", "Transfermóvil"),
        UssdCode(
            "t19", "TM · Registrar tarjeta BANDEC", "*444*49*02*{tarjeta}#",
            "Asociar tarjeta BANDEC a Transfermóvil", "Transfermóvil",
            needsParams = true, paramHints = listOf("Número de tarjeta")
        ),
        UssdCode(
            "t20", "TM · Registrar tarjeta BPA", "*444*49*01*{tarjeta}#",
            "Asociar tarjeta BPA a Transfermóvil", "Transfermóvil",
            needsParams = true, paramHints = listOf("Número de tarjeta")
        ),
        UssdCode(
            "t21", "TM · Registrar tarjeta BANMET", "*444*49*03*{tarjeta}#",
            "Asociar tarjeta BANMET a Transfermóvil", "Transfermóvil",
            needsParams = true, paramHints = listOf("Número de tarjeta")
        )
    )

    val categories = listOf(
        "Todos", "Favoritos", "Recientes", "Consultas", "Planes", "Recargas",
        "Llamadas", "Internacional", "Dispositivo", "Atención", "Emergencias", "Transfermóvil"
    )

    val shortcuts = listOf(
        allCodes.first { it.id == "c1" },
        allCodes.first { it.id == "c2" },
        allCodes.first { it.id == "p1" },
        allCodes.first { it.id == "p3" }
    )

    /** Accesos rápidos tipo QvaCall / UtilEs (panel principal) */
    val quickPanel = listOf(
        allCodes.first { it.id == "c1" },  // Saldo
        allCodes.first { it.id == "c2" },  // Datos
        allCodes.first { it.id == "c3" },  // Bono
        allCodes.first { it.id == "p3" },  // Transferir
        allCodes.first { it.id == "r2" },  // Recargar
        allCodes.first { it.id == "l1" },  // *99
        allCodes.first { it.id == "p1" },  // Planes
        allCodes.first { it.id == "c6" }   // Límite 360
    )

    private val synonyms = mapOf(
        "megas" to listOf("datos", "internet", "paquete", "328"),
        "internet" to listOf("datos", "megas", "328", "468"),
        "paquete" to listOf("datos", "planes", "133"),
        "bono" to listOf("266", "promoción", "usd", "lte"),
        "minutos" to listOf("voz", "869", "llamadas"),
        "mensaje" to listOf("sms", "767"),
        "sms" to listOf("mensaje", "767"),
        "transferir" to listOf("234", "saldo", "transferencia", "p3"),
        "recarga" to listOf("666", "662", "732"),
        "saldo" to listOf("222", "consulta"),
        "imei" to listOf("06", "dispositivo"),
        "banco" to listOf("444", "transfermóvil", "tm", "bandec", "bpa", "banmet"),
        "emergencia" to listOf("103", "104", "105", "106", "107"),
        "anonima" to listOf("31", "ocultar", "anónima"),
        "99" to listOf("cobro", "revertido", "sin saldo"),
        "amigos" to listOf("264", "plan amigos"),
        "desvio" to listOf("21", "67", "61", "desvío", "desviar"),
        "nauta" to listOf("59", "recarga nauta"),
        "luz" to listOf("41", "electricidad"),
        "gas" to listOf("67"),
        "multa" to listOf("44"),
        "onat" to listOf("43", "impuesto"),
        "todus" to listOf("133", "datos"),
        "adelanta" to listOf("234", "adelanto")
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
