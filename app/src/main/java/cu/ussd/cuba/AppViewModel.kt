package cu.ussd.cuba

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AppViewModel : ViewModel() {
    private val _query = MutableLiveData("")
    val query: LiveData<String> = _query

    private val _tick = MutableLiveData(0L)
    val tick: LiveData<Long> = _tick

    /** Cambia al elegir un estilo de interfaz; fuerza redibujado de listas. */
    private val _styleTick = MutableLiveData(0L)
    val styleTick: LiveData<Long> = _styleTick

    fun setQuery(q: String) {
        if (_query.value != q) _query.value = q
    }

    fun notifyDataChanged() {
        _tick.value = System.currentTimeMillis()
    }

    fun notifyStyleChanged() {
        _styleTick.value = System.currentTimeMillis()
        _tick.value = System.currentTimeMillis()
    }
}
