package com.guideai.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val captureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ScreenCapture.start(this, result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 32) }
        layout.addView(TextView(this).apply { text = "Guide AI\n\nEnable screen access and overlay permission to get step-by-step help in other apps."; textSize = 20f })
        layout.addView(Button(this).apply { text = "Open Accessibility Settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        layout.addView(Button(this).apply { text = "Allow Floating Guide Button"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        layout.addView(Button(this).apply {
            text = "Allow Screen Capture (one time)"
            setOnClickListener {
                val manager = getSystemService(MediaProjectionManager::class.java)
                captureRequest.launch(manager.createScreenCaptureIntent())
            }
        })
        setContentView(layout)
    }
}
