package com.guideai.app

import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val captureRequest = 4201
    private val ink = Color.rgb(24, 28, 38)
    private val muted = Color.rgb(92, 98, 112)
    private val amber = Color.rgb(245, 178, 75)
    private val soft = Color.rgb(247, 244, 239)

    private fun text(value: String, size: Float, color: Int = ink): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            setPadding(0, 0, 0, 8)
        }

    private fun button(
        label: String,
        primary: Boolean = false,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(if (primary) ink else Color.WHITE)
        setBackgroundColor(if (primary) amber else Color.rgb(42, 48, 62))
        minHeight = 56
        setPadding(18, 0, 18, 0)
        setOnClickListener { action() }
    }

    private fun section(title: String, description: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.WHITE)
            addView(text(title, 18f))
            addView(text(description, 13f, muted))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ink
        window.navigationBarColor = ink

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(soft)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 32)
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        brand.addView(TextView(this).apply {
            text = "G"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setBackgroundColor(amber)
            setPadding(14, 4, 14, 4)
        })

        brand.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 0, 0)
            addView(text("Guide AI", 24f))
            addView(text("Your calm copilot, everywhere", 13f, muted))
        })

        content.addView(brand)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ink)
        }

        hero.addView(text("Get unstuck,\none step at a time.", 30f, Color.WHITE))
        hero.addView(
            text(
                "Guide AI reads the screen only with your permission and explains what to do next.",
                14f,
                Color.LTGRAY
            )
        )

        hero.addView(
            button("Activate Guide AI", true) {
                GuideSettings.setActive(this@MainActivity, true)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, 60).apply {
                    topMargin = 18
                }
            }
        )

        content.addView(hero)
        content.addView(text("SETUP", 11f, muted).apply {
            setPadding(4, 28, 0, 8)
        })

        val access = section(
            "Screen access",
            "Required to understand visible text in other apps."
        )

        access.addView(button("Open Accessibility Settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        content.addView(
            access,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 10
            }
        )

        val overlay = section(
            "Floating helper",
            "Show the Guide AI button above the app you are using."
        )

        overlay.addView(button("Allow Floating Guide Button") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })

        content.addView(
            overlay,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 10
            }
        )

        val capture = section(
            "Visual understanding",
            "Optional. Share a one-time screenshot for Gemini Vision analysis."
        )

        capture.addView(button("Allow Screen Capture (one time)") {
            val manager = getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                captureRequest
            )
        })

        content.addView(
            capture,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 10
            }
        )

        content.addView(text("PREFERENCES", 11f, muted).apply {
            setPadding(4, 20, 0, 8)
        })

        val preferences = section(
            "Your preferences",
            "Choose how Guide AI speaks and responds."
        )

        val languages = arrayOf("Hindi", "English", "اردو", "বাংলা")

        preferences.addView(text("Guidance language", 14f).apply {
            setPadding(0, 12, 0, 4)
        })

        preferences.addView(
            Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    languages
                )

                setSelection(
                    languages.indexOf(
                        GuideSettings.language(this@MainActivity)
                    ).coerceAtLeast(0)
                )

                onItemSelectedListener =
                    object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(
                            parent: android.widget.AdapterView<*>?
                        ) = Unit

                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            GuideSettings.setLanguage(
                                this@MainActivity,
                                languages[position]
                            )
                        }
                    }
            }
        )

        preferences.addView(CheckBox(this).apply {
            text = "Voice guidance"
            textSize = 15f
            isChecked = GuideSettings.voiceEnabled(this@MainActivity)

            setOnCheckedChangeListener { _, checked ->
                GuideSettings.setVoiceEnabled(
                    this@MainActivity,
                    checked
                )
            }
        })

        preferences.addView(CheckBox(this).apply {
            text = "I understand screen content is sent temporarily"
            textSize = 14f
            isChecked = GuideSettings.hasConsent(this@MainActivity)

            setOnCheckedChangeListener { _, checked ->
                GuideSettings.setConsent(
                    this@MainActivity,
                    checked
                )
            }
        })

        content.addView(
            preferences,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 10
            }
        )

        val privacy = section(
            "Privacy first",
            "Guide AI never stores screenshots permanently. You can turn it off at any time."
        )

        privacy.addView(button("Turn Guide AI off") {
            GuideSettings.setActive(this@MainActivity, false)
        })

        content.addView(privacy)

        content.addView(
            text(
                "Guide AI only provides suggestions. Always verify important actions yourself.",
                12f,
                muted
            ).apply {
                setPadding(4, 24, 4, 0)
            }
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == captureRequest &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            ScreenCapture.start(this, resultCode, data)
        }
    }
}
