package com.najishab.one.ui.najiwarp

import android.app.Activity
import android.os.Bundle
import com.najishab.one.engines.najiwarp.NajiWarpZtCallbackHolder

class NajiWarpZtCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { uri ->
            NajiWarpZtCallbackHolder.pendingCallbackUri.value = uri
        }
        finish()
    }
}