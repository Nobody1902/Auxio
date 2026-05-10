/*
 * Copyright (c) 2024 Auxio Project
 * LyricsListFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.home.list

import android.animation.ValueAnimator
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.target
import coil3.request.transformations
import com.google.android.material.R as MR
import org.oxycblt.auxio.image.coil.ScaleBlurTransformation
import org.oxycblt.auxio.util.getAttrColorCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.databinding.FragmentLyricsListBinding
import org.oxycblt.auxio.lyrics.LyricLine
import org.oxycblt.auxio.lyrics.LyricsViewModel
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Song

@AndroidEntryPoint
class LyricsListFragment : ViewBindingFragment<FragmentLyricsListBinding>() {

    @Inject lateinit var imageLoader: ImageLoader

    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val lyricsModel: LyricsViewModel by activityViewModels()

    override fun onCreateBinding(inflater: LayoutInflater): FragmentLyricsListBinding {
        return FragmentLyricsListBinding.inflate(inflater)
    }

    override fun onBindingCreated(binding: FragmentLyricsListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        val surfaceColor = requireContext().getAttrColorCompat(MR.attr.colorSurface).defaultColor
        val transparent = android.graphics.Color.argb(0, 0, 0, 0)
        val surfaceFaded = android.graphics.Color.argb(100,
            android.graphics.Color.red(surfaceColor),
            android.graphics.Color.green(surfaceColor),
            android.graphics.Color.blue(surfaceColor))
        binding.lyricsGradient.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(surfaceColor, surfaceFaded, transparent, transparent)
        )

        binding.lyricsFollowToggle.isChecked = true
        (binding.lyricsFollowToggle as com.google.android.material.button.MaterialButton).text = "Following"
        binding.lyricsFollowToggle.setOnClickListener {
            val toggled = (it as com.google.android.material.button.MaterialButton).isChecked
            it.text = if (toggled) "Following" else "Follow"
            lyricsModel.toggleFollow()
            if (toggled) {
                val idx = lyricsModel.currentLineIndex.value
                if (idx >= 0) {
                    scrollToLineCenteredAnimated(idx)
                }
            } else {
                resetAllLyricsToDefault()
            }
        }

        collectImmediately(playbackModel.song, ::updateSong)
        collectImmediately(playbackModel.positionDs) { positionDs ->
            lyricsModel.updatePlaybackPosition(positionDs * 100)
        }
        collectImmediately(lyricsModel.lyricsLines, ::updateLyrics)
        collectImmediately(lyricsModel.currentLineIndex, ::updateCurrentLine)
    }

    override fun onDestroyBinding(binding: FragmentLyricsListBinding) {
        binding.lyricsFollowToggle.setOnClickListener(null)
    }

    private fun updateSong(song: Song?) {
        val b = binding ?: return
        if (song != null) {
            val request =
                ImageRequest.Builder(requireContext())
                    .data(song.cover)
                    .transformations(ScaleBlurTransformation(25, 0.5f))
                    .target(b.lyricsCover)
                    .build()
            imageLoader.enqueue(request)
        } else {
            b.lyricsCover.setImageDrawable(null)
        }
        lyricsModel.onSongChanged(song)
        b.lyricsScroll.scrollTo(0, 0)

        if (song == null) {
            b.lyricsNoSong.isVisible = true
            b.lyricsNoLyrics.isVisible = false
            b.lyricsContainer.isVisible = false
            b.lyricsFollowToggle.isVisible = false
        } else {
            b.lyricsFollowToggle.isVisible = true
        }
    }

    private fun updateLyrics(lines: List<LyricLine>?) {
        val b = binding ?: return
        b.lyricsContainer.removeAllViews()

        if (lines.isNullOrEmpty()) {
            b.lyricsNoLyrics.isVisible = true
            b.lyricsContainer.isVisible = false
            return
        }

        b.lyricsNoSong.isVisible = false
        b.lyricsNoLyrics.isVisible = false
        b.lyricsContainer.isVisible = true

        val context = requireContext()

        lines.forEachIndexed { index, line ->
            val textView = android.widget.TextView(context).apply {
                text = line.text
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                alpha = 0.7f
                setLineSpacing(8f, 1f)
                setPadding(0, 14, 0, 14)
                isClickable = line.timeMs >= 0
                setOnClickListener {
                    if (line.timeMs >= 0) {
                        lyricsModel.seekToLine(index)
                    }
                }
            }

            b.lyricsContainer.addView(textView)
        }
    }

    private fun updateCurrentLine(index: Int) {
        val isFollowEnabled = binding?.lyricsFollowToggle?.isChecked ?: true
        val b = binding ?: return
        val container = b.lyricsContainer

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? android.widget.TextView ?: continue
            val isActive = i == index

            child.animate().setListener(null).cancel()

            if (isActive) {
                child.setTypeface(null, android.graphics.Typeface.BOLD)
                child.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(400)
                    .start()
            } else {
                child.setTypeface(null, android.graphics.Typeface.NORMAL)
                child.animate()
                    .alpha(0.5f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(400)
                    .start()
            }
        }

        if (isFollowEnabled && index >= 0) {
            scrollToLineCenteredAnimated(index)
        }
    }

    private fun scrollToLineCenteredAnimated(index: Int) {
        val b = binding ?: return
        val container = b.lyricsContainer
        if (index < 0 || index >= container.childCount) return

        val view = container.getChildAt(index)
        val scrollView = b.lyricsScroll

        scrollView.post {
            val targetY = (view.top - scrollView.height / 2 + view.height / 2).coerceAtLeast(0)
            val startY = scrollView.scrollY
            if (startY == targetY) return@post

            ValueAnimator.ofInt(startY, targetY).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    scrollView.scrollTo(0, anim.animatedValue as Int)
                }
                start()
            }
        }
    }

    private fun resetAllLyricsToDefault() {
        val b = binding ?: return
        val container = b.lyricsContainer
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? android.widget.TextView ?: continue
            child.animate().setListener(null).cancel()
            child.setTypeface(null, android.graphics.Typeface.NORMAL)
            child.animate()
                .alpha(0.7f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .start()
        }
    }

    private fun Int.dpToPx(): Int =
        (this * Resources.getSystem().displayMetrics.density).toInt()
}
