package com.v2ray.ang.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.v2ray.ang.ui.flow.FlowActivity

class UrlSchemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.host in setOf("install-config", "install-sub") }?.let { link ->
                link.getQueryParameter("url")?.let { url ->
                    val name = link.fragment
                    if (!name.isNullOrBlank() && Uri.parse(url).fragment.isNullOrBlank()) "$url#${Uri.encode(name)}" else url
                }
            }
            else -> null
        }
        if (!payload.isNullOrBlank()) {
            startActivity(Intent(this, FlowActivity::class.java).putExtra(FlowActivity.EXTRA_IMPORT, payload))
        }
        finish()
    }
}

