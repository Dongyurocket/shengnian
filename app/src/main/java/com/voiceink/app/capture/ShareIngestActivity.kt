package com.voiceink.app.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 透明 Activity：接住系统分享（ACTION_SEND 文本）直接落库（§6.2） */
@AndroidEntryPoint
class ShareIngestActivity : ComponentActivity() {

    @Inject
    lateinit var captureController: CaptureController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        lifecycleScope.launch {
            if (!text.isNullOrBlank()) {
                captureController.capture(text, source = "share")
                Toast.makeText(this@ShareIngestActivity, "已保存，AI 整理中…", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
