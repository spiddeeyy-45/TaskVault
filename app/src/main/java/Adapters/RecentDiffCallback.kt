package Adapters

import DataClass.RecentSearch
import androidx.recyclerview.widget.DiffUtil

class RecentDiffCallback(
    private val oldList: List<RecentSearch>,
    private val newList: List<RecentSearch>
) : DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].username ==
                newList[newItemPosition].username
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] ==
                newList[newItemPosition]
    }
}