package Adapters
import DataClass.SessionModel
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.taskvault.R
import com.example.taskvault.databinding.SessionLayoutBinding
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(
    private val list: List<SessionModel>,
    private val onClick: (SessionModel) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: SessionLayoutBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: SessionModel) {

            //  Title
            binding.tvSessionTitle.text = session.title

            //  Date
            binding.tvSessionDate.text = formatDate(session.timestamp)

            //  Description
            binding.tvSessionDescription.text = session.description

            //  Duration
            val totalSeconds = session.durationMinutes

            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            binding.tvSessionDuration.text =
                "${minutes}m ${seconds}s"

            //  Warning Text Row
            binding.tvSessionPages.text =
                "${session.warnings} warnings"

            //  Warning Badge (red pill)
            if (session.warnings > 0) {
                binding.cvWarningBadge.visibility = View.VISIBLE
                binding.tvWarningCount.text =
                    session.warnings.toString()
            } else {
                binding.cvWarningBadge.visibility = View.GONE
            }

            //  Status Badge Text
            binding.tvSessionStatus.text = session.status

            //  Status Color Logic
            when (session.status.lowercase()) {
                "completed" -> {
                    binding.cvStatusBadge.setCardBackgroundColor(
                        Color.parseColor("#26D4AF37")
                    )
                    binding.tvSessionStatus.setTextColor(
                        Color.parseColor("#D4AF37")
                    )
                }

                "active" -> {
                    binding.cvStatusBadge.setCardBackgroundColor(
                        Color.parseColor("#264CAF50")
                    )
                    binding.tvSessionStatus.setTextColor(
                        Color.parseColor("#4CAF50")
                    )
                }

                "interrupted" -> {
                    binding.cvStatusBadge.setCardBackgroundColor(
                        Color.parseColor("#26FF6B6B")
                    )
                    binding.tvSessionStatus.setTextColor(
                        Color.parseColor("#FF6B6B")
                    )
                }
            }

            //  Cover Image Logic
            if (!session.coverImageUrl.isNullOrEmpty()) {
                binding.ivSessionCover.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(session.coverImageUrl)
                    .placeholder(R.drawable.image)
                    .into(binding.ivSessionCover)
                binding.tvSessionProgress.text = "Cover"
                binding.tvSessionProgress.setTextColor(
                    Color.parseColor("#D4AF37")
                )
            } else {
                binding.tvSessionProgress.text = "No Cover"
                binding.tvSessionProgress.setTextColor(
                    Color.parseColor("#70A68A64")
                )
            }

            //  Click Listener
            binding.root.setOnClickListener {
                onClick(session)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val binding = SessionLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(list[position])
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat(
            "MMM dd, hh:mm a",
            Locale.getDefault()
        )
        return sdf.format(Date(timestamp))
    }
}