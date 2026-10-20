package com.nihalthakral.nihalhome

import android.graphics.Color

data class GameInfo(
    val title: String,
    val tagline: String,
    val emoji: String,
    val asset: String,
    val colorStart: Int,
    val colorEnd: Int
) {
    val url: String get() = "file:///android_asset/$asset"
}

object GameCatalog {
    val games: List<GameInfo> = listOf(
        GameInfo(
            title = "Block Hop",
            tagline = "Tap to jump and keep hopping",
            emoji = "\uD83C\uDFAE",
            asset = "game.html",
            colorStart = Color.parseColor("#1F8A83"),
            colorEnd = Color.parseColor("#2B4541")
        ),
        GameInfo(
            title = "Gendistan",
            tagline = "Ek alag hi desh, jahan ball ki jaan khatre mein hai",
            emoji = "\uD83C\uDFAE",
            asset = "neon_slip.html",
            colorStart = Color.parseColor("#3B2FB8"),
            colorEnd = Color.parseColor("#C2306A")
        )
    )

    val latestIndex: Int get() = games.lastIndex
}
