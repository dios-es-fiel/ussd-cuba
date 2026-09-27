package cu.ussd.cuba

data class UssdCode(
    val id: String,
    val title: String,
    val code: String,
    val description: String,
    val category: String,
    val needsParams: Boolean = false,
    val paramHints: List<String> = emptyList()
)
