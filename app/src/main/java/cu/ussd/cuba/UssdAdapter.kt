package cu.ussd.cuba

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cu.ussd.cuba.databinding.ItemUssdBinding

class UssdAdapter(
    private val onClick: (UssdCode) -> Unit,
    private val onLongClick: (UssdCode) -> Unit,
    private val onFavoriteClick: (UssdCode) -> Unit,
    private val isFavorite: (String) -> Boolean
) : ListAdapter<UssdCode, UssdAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<UssdCode>() {
        override fun areItemsTheSame(old: UssdCode, new: UssdCode) = old.id == new.id
        override fun areContentsTheSame(old: UssdCode, new: UssdCode) = old == new
    }

    class ViewHolder(val binding: ItemUssdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUssdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.tvTitle.text = item.title
        b.tvCode.text = item.code.replace(Regex("\\{[^}]+\\}"), "…")
        b.tvDescription.text = item.description
        b.tvCategory.text = item.category

        val fav = isFavorite(item.id)
        b.btnFavorite.setImageResource(
            if (fav) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        b.btnFavorite.setColorFilter(
            ContextCompat.getColor(
                b.root.context,
                if (fav) android.R.color.holo_orange_light else android.R.color.darker_gray
            )
        )

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
        b.btnFavorite.setOnClickListener { onFavoriteClick(item) }
    }
}
