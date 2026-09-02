package com.guideai.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

import com.guideai.app.GuideSettings
import com.guideai.app.GuideOverlay
import com.guideai.app.CaptureService
import com.guideai.app.ScreenCapture

class MainActivity : AppCompatActivity() {

    private lateinit var guideToggleButton: Button

    private val captureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                GuideSettings.setActive(this, true)
                ScreenCapture.start(this, result.resultCode, result.data!!)
                GuideOverlay.show(this)
                updateToggleText()
                Toast.makeText(this, "Screen capture ready!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 32)
        }

        layout.addView(TextView(this).apply { text = "Guide AI"; textSize = 28f })
        layout.addView(TextView(this).apply {
            text = "\nEnable screen access, overlay and screen capture permission so the app can take a screenshot and give exact guidance.\n"
            textSize = 16f
        })

        layout.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        layout.addView(Button(this).apply {
            text = "Allow Floating Guide Button"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        layout.addView(Button(this).apply {
            text = "Allow Screen Capture (one time)"
            setOnClickListener {
                try {
                    val manager = getSystemService(MediaProjectionManager::class.java)
                    captureRequest.launch(manager.createScreenCaptureIntent())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })

        layout.addView(TextView(this).apply { text = "\nPrivacy"; textSize = 16f })
        layout.addView(CheckBox(this).apply {
            text = "I understand screen content is sent temporarily for guidance"
            isChecked = GuideSettings.hasConsent(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> GuideSettings.setConsent(this@MainActivity, checked) }
        })

        layout.addView(CheckBox(this).apply {
            text = "Voice guidance"
            isChecked = GuideSettings.voiceEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> GuideSettings.setVoiceEnabled(this@MainActivity, checked) }
        })

        layout.addView(TextView(this).apply { text = "" })

        guideToggleButton = Button(this)
        layout.addView(guideToggleButton)

        // Force OFF by default if not set explicitly
        if (!GuideSettings.hasActiveKey(this)) {
            GuideSettings.setActive(this, false)
        }
        
        updateToggleText()
        
        guideToggleButton.setOnClickListener {
            val current = GuideSettings.isActive(this@MainActivity)
            val newState = !current
            GuideSettings.setActive(this@MainActivity, newState)
            if (!newState) {
                GuideOverlay.forceHide()
                try {
                    stopService(Intent(this@MainActivity, CaptureService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Toast.makeText(this@MainActivity, "Guide AI is now OFF", Toast.LENGTH_SHORT).show()
            } else {
                GuideOverlay.show(this@MainActivity)
                Toast.makeText(this@MainActivity, "Guide AI is now ON — Guide AI ready", Toast.LENGTH_SHORT).show()
            }
            updateToggleText()
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        if (::guideToggleButton.isInitialized) updateToggleText()
        if (GuideSettings.isActive(this)) {
            GuideOverlay.show(this)
        } else {
            GuideOverlay.forceHide()
        }
    }

    private fun updateToggleText() {
        val isActive = GuideSettings.isActive(this)
        guideToggleButton.text = if (isActive) "GUIDE AI IS ON — TAP TO TURN OFF" else "GUIDE AI IS OFF — TAP TO TURN ON"
    }
}
