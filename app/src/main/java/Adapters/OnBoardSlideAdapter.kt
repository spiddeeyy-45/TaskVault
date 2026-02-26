package Adapters

import DataClass.OnBoardSlide
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taskvault.databinding.ItemOnboardingPageBinding

class OnBoardSlideAdapter(
    private val slideList: List<OnBoardSlide>
) : RecyclerView.Adapter<OnBoardSlideAdapter.SlideViewHolder>() {

    inner class SlideViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slide: OnBoardSlide) {

            // Lottie animation
            binding.lottieView.setAnimation(slide.animationRes)
            binding.lottieView.playAnimation()

            // Text content
            binding.tvTag.text = slide.tag
            binding.tvTitle.text = slide.title
            binding.tvSubtitle.text = slide.subtitle
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SlideViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(slideList[position])
    }

    override fun getItemCount(): Int = slideList.size
}