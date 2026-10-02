package cu.ussd.cuba

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import cu.ussd.cuba.databinding.ItemUssdBinding

class UssdAdapter(
    private val onClick: (UssdCode) -> Unit,
    private val onLongClick: (UssdCode) -> Unit,
    private val onFavoriteClick: (UssdCode) -> Unit,
    private val isFavorite: (String) -> Boolean,
    private val styleProvider: () -> ThemeHelper.UiStyle = {
        ThemeHelper.uiStyle("clasico")
    }
) : ListAdapter<UssdCode, UssdAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<UssdCode>() {
        override fun areItemsTheSame(old: UssdCode, new: UssdCode) = old.id == new.id
        override fun areContentsTheSame(old: UssdCode, new: UssdCode) = old == new
    }

    class ViewHolder(val binding: ItemUssdBinding) : RecyclerView.ViewHolder(binding.root)

    fun forceRestyle() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUssdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val style = styleProvider()
        val density = ctx.resources.displayMetrics.density

        fun dp(v: Int) = (v * density).toInt()
        fun dpF(v: Float) = v * density

        val card = b.root as MaterialCardView
        card.radius = dpF(style.cornerRadiusDp)
        card.cardElevation = dpF(style.cardElevationDp)
        card.useCompatPadding = style.cardElevationDp > 0f

        // Borde sutil (outline), nunca primary — evita aspecto amateur
        if (style.strokeWidthDp > 0f) {
            card.strokeWidth = dpF(style.strokeWidthDp).toInt().coerceAtLeast(1)
            val tv = TypedValue()
            if (ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, tv, true)) {
                card.strokeColor = tv.data
            }
        } else {
            card.strokeWidth = 0
        }

        val lp = card.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            val m = dp(style.itemMarginVDp)
            lp.topMargin = m
            lp.bottomMargin = m
            card.layoutParams = lp
        }

        val inner = card.getChildAt(0) as? LinearLayout
        inner?.updatePadding(
            left = dp(style.itemPaddingDp),
            top = dp(style.itemPaddingDp),
            right = dp(4),
            bottom = dp(style.itemPaddingDp)
        )

        b.accentBar.isVisible = style.showAccentBar
        if (style.showAccentBar) {
            b.accentBar.minimumHeight = dp(36)
        }

        b.tvTitle.text = item.title
        b.tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.titleSp)

        val codeText = item.code.replace(Regex("\\{[^}]+\\}"), "…")
        b.tvCode.text = codeText
        b.tvCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.codeSp)

        b.tvDescription.text = item.description
        b.tvDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.descSp)
        b.tvDescription.isVisible = style.showDescription && item.description.isNotBlank()

        val favSize = dp(style.favButtonDp)
        b.btnFavorite.updateLayoutParams {
            width = favSize
            height = favSize
        }

        val fav = isFavorite(item.id)
        b.btnFavorite.setImageResource(
            if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        // Quitar tint residual de versiones anteriores
        b.btnFavorite.clearColorFilter()

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
        b.btnFavorite.setOnClickListener { onFavoriteClick(item) }
    }
}
