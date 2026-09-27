package cu.ussd.cuba

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cu.ussd.cuba.databinding.ItemUssdBinding

class UssdAdapter(
    private var items: List<UssdCode>,
    private val onClick: (UssdCode) -> Unit
) : RecyclerView.Adapter<UssdAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemUssdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUssdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvCode.text = item.code
        holder.binding.tvDescription.text = item.description
        holder.binding.tvCategory.text = item.category
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<UssdCode>) {
        items = newItems
        notifyDataSetChanged()
    }
}
