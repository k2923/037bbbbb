package com.goseries4k

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Store episode identity only: player URLs and tokens must be discovered again at playback. */
internal data class SeriesPlayback(val page: String, val ids: List<String>) {
    fun encode(): String = mapper.writeValueAsString(mapOf("page" to page, "ids" to ids))
    companion object {
        private val mapper = ObjectMapper()
        fun decode(data: String): SeriesPlayback {
            val json = mapper.readTree(data)
            val page = requireNotNull(GoSeriesParser.local(json.path("page").asText())) { "หน้าซีรีส์ไม่ถูกต้อง" }
            val ids = json.path("ids").map { it.asText() }.filter { it.matches(Regex("[0-9]+")) }.distinct()
            require(ids.isNotEmpty()) { "ไม่พบรหัสตอน" }
            return SeriesPlayback(page, ids)
        }
    }
}

internal object TorboPlayer {
    val capturePattern = Regex("^https://torbo007\\.com/api/stream/[a-f0-9]+/index\\.m3u8(?:\\?.*)?$")
    fun embed(value: String): String? = value.toHttpUrlOrNull()?.takeIf {
        it.scheme == "https" && it.host == "torbo007.com" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPath.matches(Regex("/embed/[a-f0-9]+"))
    }?.toString()
    fun media(value: String, player: String): Boolean = embed(player) != null && capturePattern.matches(value) &&
        value.toHttpUrlOrNull()?.let { it.username.isEmpty() && it.password.isEmpty() && it.port == 443 } == true
}

internal object EpisodeSelection {
    private val mapper = ObjectMapper()
    /** A changed data-post-id alone is insufficient: the old episode's iframe may still exist. */
    fun selected(id: String, snapshot: String): String? = runCatching {
        val decoded = mapper.readTree(snapshot)
        val state = if (decoded.isTextual) mapper.readTree(decoded.asText()) else decoded
        if (state.path("id").asText() != id || state.path("currentId").asText() != id) return null
        val player = TorboPlayer.embed(state.path("player").asText()) ?: return null
        if (state.path("initialId").asText() != id && state.path("initialPlayer").asText() == player) return null
        player
    }.getOrNull()

    fun script(id: String): String {
        require(id.matches(Regex("[0-9]+")))
        return """
            (function() {
              var id = '$id';
              var state = window.__cloudstreamGoSeries;
              if (!state) {
                state = window.__cloudstreamGoSeries = {id:id, clicked:false, initialId:null, initialPlayer:null};
                var tick = function() {
                  var root = document.querySelector('#mpPlayer');
                  var button = document.querySelector('button.mp-ep-btn[data-id="' + id + '"]');
                  if (!root || !button || document.readyState !== 'complete') return;
                  var frame = root.querySelector('iframe[src]');
                  if (state.initialId === null) {
                    state.initialId = root.getAttribute('data-post-id') || '';
                    state.initialPlayer = frame ? frame.src : '';
                  }
                  if (!state.clicked && state.initialId !== id) {
                    state.clicked = true;
                    button.click();
                  }
                  state.currentId = root.getAttribute('data-post-id') || '';
                  frame = root.querySelector('iframe[src]');
                  state.player = frame ? frame.src : '';
                };
                state.tick = tick;
                tick();
                state.timer = setInterval(tick, 100);
                setTimeout(function() { clearInterval(state.timer); }, 7500);
              }
              state.tick();
              return JSON.stringify({id:state.id, currentId:state.currentId, player:state.player,
                initialId:state.initialId, initialPlayer:state.initialPlayer});
            })();
        """.trimIndent()
    }
}
