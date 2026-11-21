package com.v2ray.ang.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.v2ray.ang.ui.flow.FlowActivity

class ScScannerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, FlowActivity::class.java).setAction(FlowActivity.ACTION_SCAN))
        finish()
    }
}

