package Adapters

import DataClass.PdfModel
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taskvault.databinding.ItemPdfPickerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PDFpickerAdapter(
    private val onClick: (PdfModel) -> Unit
) : RecyclerView.Adapter<PDFpickerAdapter.ViewHolder>() {

    private val originalList = mutableListOf<PdfModel>()
    private val filteredList = mutableListOf<PdfModel>()

    inner class ViewHolder(val binding: ItemPdfPickerBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(pdf: PdfModel) {
            binding.tvItemFileName.text = pdf.name
            binding.tvItemDate.text = formatDate(pdf.uploadedAt)

            binding.root.setOnClickListener {
                onClick(pdf)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPdfPickerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    fun updateData(newList: List<PdfModel>) {
        originalList.clear()
        originalList.addAll(newList)

        filteredList.clear()
        filteredList.addAll(newList)

        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList.clear()

        if (query.isEmpty()) {
            filteredList.addAll(originalList)
        } else {
            filteredList.addAll(
                originalList.filter {
                    it.name.contains(query, ignoreCase = true)
                }
            )
        }

        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}