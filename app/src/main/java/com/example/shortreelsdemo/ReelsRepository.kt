package com.example.shortreelsdemo

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Reel(
    val id: String,
    val mediaUrl: String,
    val type: String,
    val title: String?,
    val sourceUrl: String?,
    val episode: Int? = null
)

data class FeedResponse(
    val sessionId: String?,
    val items: List<Reel>,
    val newItems: List<Reel>,
    val hasMore: Boolean
)

data class EpisodeResponse(
    val sessionId: String?,
    val item: Reel
)

class ReelsRepository {
    companion object {
        private const val BASE = "https://shortreels-scraper-1.onrender.com"
        const val GOODSHORT_SOURCE = "https://www.goodshort.com/dramas/playlets?openCategory=1"
        const val REELSHORT_SOURCE = "https://www.reelshort.com/"
        const val FLEXTV_SOURCE = "https://www.flextv.cc/"
    }

    fun initialFeed(sourceUrl: String = GOODSHORT_SOURCE) =
        request("$BASE/v1/feed?url=\${encode(sourceUrl)}&limit=10")

    fun nextFeed(sessionId: String) =
        request("$BASE/v1/feed?sessionId=${encode(sessionId)}&limit=10")

    fun listEpisodes(sessionId: String, sourceUrl: String?): List<Int> {
        val url = buildString {
            append("$BASE/v1/episodes?sessionId=${encode(sessionId)}")
            if (!sourceUrl.isNullOrBlank()) append("&sourceUrl=${encode(sourceUrl)}")
        }
        val root = requestJson(url)
        val a = root.optJSONArray("episodes") ?: return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val n = a.optInt(i, 0)
                if (n > 0) add(n)
            }
        }
    }

    fun switchEpisode(sessionId: String, sourceUrl: String?, episode: Int): EpisodeResponse {
        val url = buildString {
            append("$BASE/v1/episode?sessionId=${encode(sessionId)}&episode=$episode")
            if (!sourceUrl.isNullOrBlank()) append("&sourceUrl=${encode(sourceUrl)}")
        }

        val root = requestJson(url)
        val x = root.optJSONObject("item")
            ?: throw IllegalStateException("Episode response is missing item")

        val media = x.optString("mediaUrl")
        val type = x.optString("type")
        if (media.isBlank() || type.equals("segment", true)) {
            throw IllegalStateException("Episode has no playable media")
        }

        return EpisodeResponse(
            sessionId = root.optString("sessionId").takeIf { it.isNotBlank() },
            item = Reel(
                id = x.optString("id", media),
                mediaUrl = media,
                type = type,
                title = x.optString("title").takeIf { it.isNotBlank() },
                sourceUrl = x.optString("sourceUrl").takeIf { it.isNotBlank() },
                episode = x.optInt("episode").takeIf { it > 0 }
            )
        )
    }

    private fun request(urlString: String): FeedResponse {
        val root = requestJson(urlString)
        return FeedResponse(
            sessionId = root.optString("sessionId").takeIf { it.isNotBlank() },
            items = parse(root.optJSONArray("items")),
            newItems = parse(root.optJSONArray("newItems")),
            hasMore = root.optBoolean("hasMore", true)
        )
    }

    private fun requestJson(urlString: String): JSONObject {
        val c = URL(urlString).openConnection() as HttpURLConnection
        c.connectTimeout = 30000
        c.readTimeout = 120000
        c.requestMethod = "GET"
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

            if (code !in 200..299) {
                val detail = try { JSONObject(body).optString("error", "request failed") } catch (_: Exception) { body }
                throw IllegalStateException("Server returned HTTP $code: $detail")
            }

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) {
                throw IllegalStateException(root.optString("error", "Request failed"))
            }
            return root
        } finally {
            c.disconnect()
        }
    }

    private fun parse(a: org.json.JSONArray?): List<Reel> {
        if (a == null) return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val x = a.optJSONObject(i) ?: continue
                val media = x.optString("mediaUrl")
                val type = x.optString("type")
                if (media.isBlank() || type.equals("segment", true)) continue

                add(
                    Reel(
                        id = x.optString("id", media),
                        mediaUrl = media,
                        type = type,
                        title = x.optString("title").takeIf { it.isNotBlank() },
                        sourceUrl = x.optString("sourceUrl").takeIf { it.isNotBlank() },
                        episode = x.optInt("episode").takeIf { it > 0 }
                    )
                )
            }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
}
