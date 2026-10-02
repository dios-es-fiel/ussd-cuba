package cu.ussd.cuba

/**
 * Estructura de módulos y subtipos para no abrumar con listas largas.
 * Cada módulo tiene tipos filtrables en la barra inferior.
 */
object ModuleStructure {

    data class Subtype(
        val id: String,
        val label: String,
        val filter: (UssdCode) -> Boolean
    )

    /** Subtipos del módulo Consultas */
    val consultas = listOf(
        Subtype("todo", "Todo") { it.category == "Consultas" },
        Subtype("saldo", "Saldo") { it.id in setOf("c1", "c9", "c11") },
        Subtype("datos", "Datos") { it.id in setOf("c2", "c3", "c8") },
        Subtype("voz_sms", "Voz · SMS") { it.id in setOf("c4", "c5") },
        Subtype("linea", "Línea") { it.id in setOf("c6", "c7", "c10", "c12", "c13", "c14") }
    )

    /** Subtipos del módulo Planes */
    val planes = listOf(
        Subtype("todo", "Todo") { it.category in setOf("Planes", "Recargas") },
        Subtype("comprar", "Comprar") {
            it.category == "Planes" && it.id !in setOf("p2", "p3", "p4", "p5")
        },
        Subtype("transferir", "Transferir") { it.id in setOf("p2", "p3", "p4", "p5") },
        Subtype("recargas", "Recargas") { it.category == "Recargas" }
    )

    /** Subtipos del módulo Llamadas */
    val llamadas = listOf(
        Subtype("todo", "Todo") {
            it.category in setOf("Llamadas", "Internacional")
        },
        Subtype("especiales", "Especiales") {
            it.id in setOf("l1", "l2", "l8", "l9")
        },
        Subtype("desvios", "Desvíos") {
            it.id in setOf("l4", "l5", "l4b", "l5b", "l4c", "l5c", "l5d")
        },
        Subtype("privacidad", "Privacidad") {
            it.id in setOf("l3", "l3b", "l6", "l7", "l10", "l11")
        },
        Subtype("intl", "Internacional") { it.category == "Internacional" }
    )

    fun subtypesFor(module: String): List<Subtype> = when (module) {
        "Consultas" -> consultas
        "Planes" -> planes
        "Llamadas" -> llamadas
        else -> emptyList()
    }
}
