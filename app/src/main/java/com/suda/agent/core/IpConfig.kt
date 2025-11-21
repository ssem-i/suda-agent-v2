package com.suda.agent.core

import android.content.Context

object IpConfig {
    private const val PREFS_NAME = "suda_prefs"
    private const val KEY = "ip_config"
    private const val DEFAULT_IP = "http://10.50.2.74:3001/"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY, null)
        return if (value.isNullOrBlank()) DEFAULT_IP else value
    }

    fun save(context: Context, input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        val normalized = if (!trimmed.endsWith("/")) "$trimmed/" else trimmed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, normalized)
            .apply()
        return normalized
    }
}


