package com.guideai.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var guideToggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 32)
        }

        layout.addView(TextView(this).apply { text = "Guide AI"; textSize = 28f })
        layout.addView(TextView(this).apply {
            text = "\nEnable screen access and overlay permission to get step-by-step help in other apps.\n"
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

        layout.addView(TextView(this).apply { text = "\nPrivacy"; textSize = 16f })
        layout.addView(CheckBox(this).apply {
            text = "I understand screen content is sent temporarily for guidance"
            isChecked = GuideSettings.hasConsent(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> GuideSettings.setConsent(this@MainActivity, checked) }
        })

        layout.addView(TextView(this).apply { text = "\nLanguage"; textSize = 16f })
        val languages = arrayOf("Hindi", "English", "اردو", "বাংলা")
        val languageSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages)
            setSelection(languages.indexOf(GuideSettings.language(this@MainActivity)).coerceAtLeast(0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    GuideSettings.setLanguage(this@MainActivity, languages[position])
                }
            }
        }
        layout.addView(languageSpinner)

        layout.addView(CheckBox(this).apply {
            text = "Voice guidance"
            isChecked = GuideSettings.voiceEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> GuideSettings.setVoiceEnabled(this@MainActivity, checked) }
        })

        layout.addView(TextView(this).apply { text = "" })

        guideToggleButton = Button(this)
        layout.addView(guideToggleButton)
        updateToggleText()
        guideToggleButton.setOnClickListener {
            val current = GuideSettings.isActive(this@MainActivity)
            GuideSettings.setActive(this@MainActivity, !current)
            updateToggleText()
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onResume() {
        super.onResume()
        if (::guideToggleButton.isInitialized) updateToggleText()
    }

    private fun updateToggleText() {
        val isActive = GuideSettings.isActive(this)
        guideToggleButton.text = if (isActive) "Guide AI is ON — tap to turn OFF" else "Guide AI is OFF — tap to turn ON"
    }
}
