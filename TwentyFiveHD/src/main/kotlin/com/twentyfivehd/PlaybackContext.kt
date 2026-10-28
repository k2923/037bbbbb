package com.twentyfivehd

import com.fasterxml.jackson.databind.ObjectMapper

/** Keep the original detail page as the player Referer, accepting previously saved embed URLs. */
internal data class PlaybackContext(val player: String, val page: String) {
    fun encode(): String = mapper.writeValueAsString(mapOf("player" to player, "page" to page))
    companion object {
        private val mapper = ObjectMapper()
        fun decode(data: String): PlaybackContext {
            if (!data.trimStart().startsWith('{')) return PlaybackContext(data, data)
            val value = mapper.readTree(data)
            val player = value.path("player").asText()
            val page = value.path("page").asText()
            require(player.startsWith("https://") || player.startsWith("http://")) { "ไม่พบลิงก์ตัวเล่น" }
            require(page.startsWith("https://") || page.startsWith("http://")) { "ไม่พบหน้ารายละเอียด" }
            return PlaybackContext(player, page)
        }
    }
}
