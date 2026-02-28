package Adapters

import DataClass.RecentSearch
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.taskvault.R
import com.example.taskvault.databinding.ItemRecentSearchBinding

class RecentSearchAdapter(
    private val onItemClick: (RecentSearch) -> Unit,
    private val onDeleteClick: (RecentSearch) -> Unit
) : RecyclerView.Adapter<RecentSearchAdapter.ViewHolder>() {

    private val list = mutableListOf<RecentSearch>()

    inner class ViewHolder(
        private val binding: ItemRecentSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentSearch) {

            binding.tvRecentUsername.text = item.username
            binding.root.setOnClickListener {
                onItemClick(item)
            }
            binding.btnRemoveRecent.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    fun submitList(newList: List<RecentSearch>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}