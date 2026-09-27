package cu.ussd.cuba

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AppViewModel : ViewModel() {
    private val _query = MutableLiveData("")
    val query: LiveData<String> = _query

    fun setQuery(q: String) {
        if (_query.value != q) _query.value = q
    }
}
