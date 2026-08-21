package com.guideai.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val captureRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                ScreenCapture.start(this, result.resultCode, result.data!!)
                Toast.makeText(this, "Screen capture ready!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                try {
                    val manager = getSystemService(MediaProjectionManager::class.java)
                    captureRequest.launch(manager.createScreenCaptureIntent())
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        layout.addView(TextView(this).apply { text = "Privacy"; textSize = 16f })
        layout.addView(CheckBox(this).apply { text = "I understand screen content is sent temporarily for guidance"; isChecked = GuideSettings.hasConsent(this@MainActivity); setOnCheckedChangeListener { _, checked -> GuideSettings.setConsent(this@MainActivity, checked) } })
        layout.addView(TextView(this).apply { text = "Language"; textSize = 16f })
        val languages = arrayOf("Hindi", "English", "اردو", "বাংলা")
        val languageSpinner = android.widget.Spinner(this).apply { adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages); setSelection(languages.indexOf(GuideSettings.language(this@MainActivity)).coerceAtLeast(0)); onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit; override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { GuideSettings.setLanguage(this@MainActivity, languages[position]) } } }
        layout.addView(languageSpinner)
        layout.addView(CheckBox(this).apply { text = "Voice guidance"; isChecked = GuideSettings.voiceEnabled(this@MainActivity); setOnCheckedChangeListener { _, checked -> GuideSettings.setVoiceEnabled(this@MainActivity, checked) } })
        layout.addView(Button(this).apply { text = "Turn Guide AI off"; setOnClickListener { GuideSettings.setActive(this@MainActivity, false); text = "Guide AI is off" } })
        setContentView(layout)
    }
}
