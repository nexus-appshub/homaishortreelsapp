package com.example.shortreelsdemo

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

class ReelPagerAdapter(
    private val items: List<Reel>,
    private val onEpisodeSelected: (position: Int, episode: Int) -> Unit
) : RecyclerView.Adapter<ReelPagerAdapter.Holder>() {

    private var activePosition = RecyclerView.NO_POSITION
    private var player: ExoPlayer? = null
    private var activeView: PlayerView? = null
    private var muted = true

    private val episodeLists = mutableMapOf<Int, List<Int>>()
    private val selectedEpisodes = mutableMapOf<Int, Int>()
    private val loadingEpisodes = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reel, parent, false)
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val reel = items[position]

        holder.title.text = reel.title ?: "Short Reel"
        holder.source.text = if (reel.sourceUrl != null) "Public source" else ""

        holder.mute.setOnClickListener {
            muted = !muted
            player?.volume = if (muted) 0f else 1f
            holder.mute.alpha = if (muted) .65f else 1f
        }

        renderEpisodes(holder, position)

        if (position == activePosition) {
            attach(holder.player, reel)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        if (holder.player == activeView) release()
        super.onViewRecycled(holder)
    }

    fun setActive(position: Int) {
        if (position == activePosition) return
        val previous = activePosition
        release()
        activePosition = position

        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (position in 0 until itemCount) notifyItemChanged(position)
    }

    fun setEpisodes(position: Int, episodes: List<Int>, selected: Int) {
        if (position !in 0 until itemCount) return
        episodeLists[position] = episodes.distinct().sorted()
        selectedEpisodes[position] = selected.coerceAtLeast(1)
        notifyItemChanged(position)
    }

    fun setEpisodeLoading(position: Int, loading: Boolean) {
        if (loading) loadingEpisodes.add(position) else loadingEpisodes.remove(position)
        if (position in 0 until itemCount) notifyItemChanged(position)
    }

    fun replaceItem(position: Int, item: Reel, selectedEpisode: Int) {
        if (position !in 0 until itemCount) return

        selectedEpisodes[position] = selectedEpisode
        release()
        notifyItemChanged(position)
    }

    private fun renderEpisodes(holder: Holder, position: Int) {
        val container = holder.episodes
        container.removeAllViews()

        val episodes = episodeLists[position].orEmpty()
        if (episodes.isEmpty()) {
            holder.episodeLabel.visibility = View.GONE
            container.visibility = View.GONE
            return
        }

        holder.episodeLabel.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        val selected = selectedEpisodes[position] ?: items[position].episode ?: episodes.first()
        val isLoading = position in loadingEpisodes
        holder.episodeLabel.text = if (isLoading) "Loading episode..." else "Episode $selected"

        episodes.forEach { number ->
            val button = Button(holder.itemView.context).apply {
                text = number.toString()
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(18, 0, 18, 0)
                textSize = 12f
                isAllCaps = false
                alpha = if (number == selected) 1f else .72f
                setTextColor(Color.WHITE)
                setOnClickListener {
                    selectedEpisodes[position] = number
                    onEpisodeSelected(position, number)
                    notifyItemChanged(position)
                }
            }
            container.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6
                }
            )
        }
    }

    private fun attach(view: PlayerView, reel: Reel) {
        release()

        player = ExoPlayer.Builder(view.context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            setMediaItem(MediaItem.fromUri(Uri.parse(reel.mediaUrl)))
            prepare()
            playWhenReady = true
        }

        view.player = player
        activeView = view
    }

    fun pauseActive() {
        player?.pause()
    }

    fun resumeActive() {
        player?.play()
    }

    private fun release() {
        activeView?.player = null
        player?.release()
        player = null
        activeView = null
    }

    fun releaseAll() {
        release()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val player: PlayerView = v.findViewById(R.id.player)
        val title: TextView = v.findViewById(R.id.title)
        val source: TextView = v.findViewById(R.id.source)
        val episodeLabel: TextView = v.findViewById(R.id.episodeLabel)
        val episodes: LinearLayout = v.findViewById(R.id.episodes)
        val mute: ImageButton = v.findViewById(R.id.muteButton)
    }
}
