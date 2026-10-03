package com.example.shortreelsdemo

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var loading: ProgressBar
    private lateinit var error: TextView
    private lateinit var adapter: ReelPagerAdapter

    private val repo = ReelsRepository()
    private val reels = mutableListOf<Reel>()
    private val episodeCache = mutableMapOf<String, List<Int>>()

    private var sessionId: String? = null
    private var loadingMore = false
    private var hasMore = true
    private var initialLoading = false
    private var episodeLoadingPosition = -1

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)

        pager = findViewById(R.id.reelsPager)
        loading = findViewById(R.id.loading)
        error = findViewById(R.id.errorText)

        adapter = ReelPagerAdapter(
            items = reels,
            onEpisodeSelected = { position, episode ->
                playEpisode(position, episode)
            }
        )
        pager.adapter = adapter
        pager.offscreenPageLimit = 1

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                adapter.setActive(position)

                if (position >= reels.size - 4) {
                    loadMore()
                }

                loadEpisodesFor(position)
            }
        })

        loadInitial()
    }

    private fun loadInitial() {
        if (initialLoading) return

        initialLoading = true
        loading.visibility = ProgressBar.VISIBLE
        error.visibility = TextView.GONE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { repo.initialFeed() }

                sessionId = response.sessionId
                    ?: throw IllegalStateException("Server did not return a session")

                hasMore = response.hasMore
                append(response.items)

                // Some source pages expose only a small first batch until the
                // browser is scrolled. Fill the initial screen to at least 10
                // items using the SAME session instead of creating another one.
                var attempts = 0
                while (reels.size < 10 && hasMore && attempts < 4) {
                    val more = withContext(Dispatchers.IO) {
                        repo.nextFeed(sessionId!!)
                    }
                    hasMore = more.hasMore
                    append(if (more.newItems.isNotEmpty()) more.newItems else more.items)
                    attempts++
                }

                if (reels.isEmpty()) {
                    error.text = "No playable reels found."
                    error.visibility = TextView.VISIBLE
                }
            } catch (e: Exception) {
                error.text = e.message ?: "Could not connect to the reels server."
                error.visibility = TextView.VISIBLE
            } finally {
                initialLoading = false
                loading.visibility = ProgressBar.GONE
            }
        }
    }

    private fun loadMore() {
        val currentSession = sessionId ?: return
        if (loadingMore || !hasMore || reels.isEmpty()) return

        loadingMore = true

        lifecycleScope.launch {
            try {
                var addedAny = false

                // The scraper may need several normal scroll/collection cycles
                // before it discovers another playable reel. Retry a small
                // bounded number of times without creating another session.
                for (attempt in 0 until 3) {
                    val response = withContext(Dispatchers.IO) {
                        repo.nextFeed(currentSession)
                    }

                    hasMore = response.hasMore
                    if (response.newItems.isNotEmpty()) {
                        val added = append(response.newItems)
                        addedAny = addedAny || added
                    }

                    if (addedAny || !hasMore) break
                    if (attempt < 2) delay(350L)
                }
            } catch (e: Exception) {
                // Keep already loaded reels visible. A later page selection
                // can retry pagination using the same session.
            } finally {
                loadingMore = false
            }
        }
    }

    private fun loadEpisodesFor(position: Int) {
        if (position !in reels.indices) return

        val source = reels[position].sourceUrl ?: return
        val currentSession = sessionId ?: return

        val cached = episodeCache[source]
        if (cached != null) {
            adapter.setEpisodes(position, cached, reels[position].episode ?: 1)
            return
        }

        lifecycleScope.launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    repo.listEpisodes(currentSession, source)
                }

                if (episodes.isNotEmpty() && position in reels.indices &&
                    reels[position].sourceUrl == source
                ) {
                    episodeCache[source] = episodes
                    adapter.setEpisodes(position, episodes, reels[position].episode ?: episodes.first())
                }
            } catch (_: Exception) {
                // Episode controls are optional; playback/feed remains usable.
            }
        }
    }

    private fun playEpisode(position: Int, episode: Int) {
        if (episodeLoadingPosition == position) return

        val currentSession = sessionId ?: return
        if (position !in reels.indices) return

        val source = reels[position].sourceUrl ?: return
        episodeLoadingPosition = position
        adapter.setEpisodeLoading(position, true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    repo.switchEpisode(currentSession, source, episode)
                }

                if (position in reels.indices && reels[position].sourceUrl == source) {
                    reels[position] = response.item
                    adapter.replaceItem(position, response.item, episode)
                    adapter.setActive(position)
                }
            } catch (_: Exception) {
                // Do not replace the currently playing episode if the selected
                // episode is not publicly playable or the session expired.
            } finally {
                episodeLoadingPosition = -1
                adapter.setEpisodeLoading(position, false)
            }
        }
    }

    private fun append(xs: List<Reel>): Boolean {
        if (xs.isEmpty()) return false

        val knownMedia = reels.asSequence()
            .map { it.mediaUrl.trim() }
            .filter { it.isNotEmpty() }
            .toHashSet()

        val add = xs.filter { item ->
            val media = item.mediaUrl.trim()
            media.isNotEmpty() &&
                !item.type.equals("segment", true) &&
                knownMedia.add(media)
        }

        if (add.isEmpty()) return false

        val oldSize = reels.size
        reels.addAll(add)
        adapter.notifyItemRangeInserted(oldSize, add.size)
        return true
    }

    override fun onPause() {
        super.onPause()
        adapter.pauseActive()
    }

    override fun onResume() {
        super.onResume()
        adapter.resumeActive()
    }

    override fun onDestroy() {
        adapter.releaseAll()
        super.onDestroy()
    }
}
