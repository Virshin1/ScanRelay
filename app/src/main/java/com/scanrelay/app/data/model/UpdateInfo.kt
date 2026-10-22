package com.scanrelay.app.data.model

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String? = null
)
