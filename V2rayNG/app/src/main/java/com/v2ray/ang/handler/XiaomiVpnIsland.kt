package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.v2ray.ang.R
import org.json.JSONObject

/** Xiaomi HyperOS 3's local notification template. Flow needs an approved application and scene. */
internal object XiaomiVpnIsland {
    private const val LOGO = "miui.focus.pic_flow_logo"
    private const val FLAG = "miui.focus.pic_flow_flag"

    fun extras(context: Context, country: String?, status: String): Bundle? {
        if (Build.VERSION.SDK_INT < 33) return null
        if (Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) < 3) return null
        val app = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        ).metaData ?: return null
        if (app.get("com.xiaomi.xms.APP_ID")?.toString().isNullOrBlank()) return null
        val business = app.get("app.flowvpn.ISLAND_BUSINESS")?.toString()?.takeIf { it.isNotBlank() } ?: return null

        val pictures = Bundle().apply {
            putParcelable(LOGO, Icon.createWithResource(context, R.drawable.flow_logo_small))
            if (country != null) putParcelable(FLAG, flagIcon(context, country))
        }
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("bigIslandArea", JSONObject()
                .put("imageTextInfoLeft", JSONObject().put("type", 1).put("picInfo", picture(LOGO)))
                .put("picInfo", picture(if (country == null) LOGO else FLAG)))
            .put("smallIslandArea", JSONObject().put("picInfo", picture(LOGO)))
        val payload = JSONObject().put("param_v2", JSONObject()
            .put("protocol", 1)
            .put("business", business)
            .put("updatable", true)
            .put("ticker", country?.let(FlowPreferences::flag) ?: "VPN")
            .put("tickerPic", LOGO)
            .put("param_island", island)
            .put("baseInfo", JSONObject().put("title", "Flow").put("content", status).put("type", 2)))
        return Bundle().apply {
            putString("miui.focus.param", payload.toString())
            putBundle("miui.focus.pics", pictures)
        }
    }

    private fun picture(key: String) = JSONObject().put("type", 1).put("pic", key)

    private fun flagIcon(context: Context, country: String): Icon {
        val resource = when (country) {
            "DE" -> R.drawable.flow_flag_de
            "FI" -> R.drawable.flow_flag_fi
            "NL" -> R.drawable.flow_flag_nl
            "PL" -> R.drawable.flow_flag_pl
            "SE" -> R.drawable.flow_flag_se
            else -> FlowPreferences.flagResource(context, country)
        }
        if (resource != null) return Icon.createWithResource(context, resource)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 62f
        }
        Canvas(bitmap).drawText(FlowPreferences.flag(country), 48f, 48f - (paint.ascent() + paint.descent()) / 2, paint)
        return Icon.createWithBitmap(bitmap)
    }
}
