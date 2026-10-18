package com.example.dadlocker

import android.content.Context

object Prefs {
    private const val NAME = "dad_locker"
    private const val KEY_ACTIVE = "prank_active"

    fun setActive(ctx: Context, active: Boolean) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE, active).apply()

    fun isActive(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
}
