package com.example.keyboard

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

data class KeyTheme(val name: String, val bg: String, val key: String, val text: String)

class VoiceKeyboardService : InputMethodService() {

    private var shift = false
    private var mode = 0 // 0 = अक्षर, 1 = सिंबल, 2 = झंडे, 3 = रंग
    private val letterKeys = mutableListOf<Button>()
    private val allKeys = mutableListOf<Button>()
    private var recognizer: SpeechRecognizer? = null
    private lateinit var root: LinearLayout
    private lateinit var lettersPanel: View
    private lateinit var symbolsPanel: View
    private lateinit var flagsPanel: View
    private lateinit var themePanel: View
    private lateinit var symbolKey: Button
    private lateinit var flagKey: Button
    private lateinit var themeKey: Button

    override fun onCreateInputView(): View {
        root = vertical()
        root.setPadding(8, 8, 8, 8)
        lettersPanel = buildLettersPanel()
        symbolsPanel = buildSymbolsPanel()
        flagsPanel = buildFlagsPanel()
        themePanel = buildThemePanel()
        root.addView(lettersPanel)
        root.addView(symbolsPanel)
        root.addView(flagsPanel)
        root.addView(themePanel)
        root.addView(buildBottomRow())
        showPanel(0)
        applyTheme(savedTheme())
        return root
    }

    private fun buildLettersPanel(): LinearLayout {
        val panel = vertical()

        val numberRow = horizontal()
        "1234567890".forEach { d ->
            numberRow.addView(makeKey(d.toString(), 1f) { commit(d.toString()) })
        }
        panel.addView(numberRow)

        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { index, row ->
            val line = horizontal()
            if (index == 2) {
                line.addView(makeKey("⇧", 1.5f) {
                    shift = !shift
                    refreshLetters()
                })
            }
            row.forEach { ch -> line.addView(makeLetterKey(ch)) }
            if (index == 2) {
                line.addView(makeKey("⌫", 1.5f) { deleteChar() })
            }
            panel.addView(line)
        }
        return panel
    }

    private fun buildSymbolsPanel(): LinearLayout {
        val panel = vertical()
        val rows = listOf(
            "@#$%&*()-+",
            "=/\\\"':;!?_",
            "<>[]{}~`|^",
            "₹€£¥¢°•±¶"
        )
        rows.forEachIndexed { index, row ->
            val line = horizontal()
            row.forEach { ch -> line.addView(makeKey(ch.toString(), 1f) { commit(ch.toString()) }) }
            if (index == 2) {
                line.addView(makeKey("⌫", 1.5f) { deleteChar() })
            }
            panel.addView(line)
        }
        return panel
    }

    private fun buildFlagsPanel(): ScrollView {
        val scroll = ScrollView(this)
        val panel = vertical()
        val codes = FLAG_CODES.split(" ")
        codes.chunked(6).forEach { chunk ->
            val line = horizontal()
            chunk.forEach { code ->
                val f = flag(code)
                line.addView(makeKey(f, 1f) { commit(f) })
            }
            repeat(6 - chunk.size) { line.addView(makeKey("", 1f) {}) }
            panel.addView(line)
        }
        scroll.addView(panel)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (240 * resources.displayMetrics.density).toInt()
        )
        return scroll
    }

    private fun buildThemePanel(): LinearLayout {
        val panel = vertical()
        THEMES.forEachIndexed { i, t ->
            val line = horizontal()
            line.addView(makeKey(t.name, 1f) {
                saveTheme(i)
                applyTheme(t)
            })
            panel.addView(line)
        }
        return panel
    }

    private fun buildBottomRow(): LinearLayout {
        val line = horizontal()
        line.addView(makeKey("🎤", 1.2f) { startVoice() })
        flagKey = makeKey("🏳", 1.2f) { showPanel(if (mode == 2) 0 else 2) }
        line.addView(flagKey)
        themeKey = makeKey("🎨", 1.2f) { showPanel(if (mode == 3) 0 else 3) }
        line.addView(themeKey)
        symbolKey = makeKey("?123", 1.5f) { showPanel(if (mode == 1) 0 else 1) }
        line.addView(symbolKey)
        line.addView(makeKey(",", 1f) { commit(",") })
        line.addView(makeKey("space", 2.5f) { commit(" ") })
        line.addView(makeKey(".", 1f) { commit(".") })
        line.addView(makeKey("↵", 1.5f) { sendEnter() })
        return line
    }

    private fun showPanel(m: Int) {
        mode = m
        lettersPanel.visibility = if (m == 0) View.VISIBLE else View.GONE
        symbolsPanel.visibility = if (m == 1) View.VISIBLE else View.GONE
        flagsPanel.visibility = if (m == 2) View.VISIBLE else View.GONE
        themePanel.visibility = if (m == 3) View.VISIBLE else View.GONE
        symbolKey.text = if (m == 1) "ABC" else "?123"
        flagKey.text = if (m == 2) "ABC" else "🏳"
        themeKey.text = if (m == 3) "ABC" else "🎨"
    }

    private fun applyTheme(t: KeyTheme) {
        root.setBackgroundColor(Color.parseColor(t.bg))
        allKeys.forEach {
            it.setBackgroundColor(Color.parseColor(t.key))
            it.setTextColor(Color.parseColor(t.text))
        }
    }

    private fun savedTheme(): KeyTheme {
        val i = getSharedPreferences("kb_prefs", MODE_PRIVATE).getInt("theme", 0)
        return THEMES[if (i in THEMES.indices) i else 0]
    }

    private fun saveTheme(i: Int) {
        getSharedPreferences("kb_prefs", MODE_PRIVATE).edit().putInt("theme", i).apply()
    }

    private fun flag(code: String): String =
        code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun makeKey(label: String, weight: Float, onClick: () -> Unit): Button {
        val h = (48 * resources.displayMetrics.density).toInt()
        return Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, h, weight).apply { setMargins(3, 3, 3, 3) }
            setOnClickListener { onClick() }
            allKeys.add(this)
        }
    }

    private fun makeLetterKey(ch: Char): Button {
        val btn = makeKey(ch.toString(), 1f) {}
        btn.setOnClickListener {
            commit(btn.text.toString())
            if (shift) {
                shift = false
                refreshLetters()
            }
        }
        letterKeys.add(btn)
        return btn
    }

    private fun refreshLetters() {
        letterKeys.forEach {
            it.text = if (shift) it.text.toString().uppercase() else it.text.toString().lowercase()
        }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteChar() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun sendEnter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun startVoice() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrEmpty()) commit("$text ")
                }
                override fun onError(error: Int) {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        recognizer?.startListening(intent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        private val THEMES = listOf(
            KeyTheme("गहरा", "#202124", "#3C4043", "#E8EAED"),
            KeyTheme("हल्का", "#F1F3F4", "#FFFFFF", "#202124"),
            KeyTheme("नीला", "#0D47A1", "#1976D2", "#FFFFFF"),
            KeyTheme("हरा", "#1B5E20", "#388E3C", "#FFFFFF"),
            KeyTheme("गुलाबी", "#880E4F", "#C2185B", "#FFFFFF")
        )

        // देशों के ISO कोड, हर कोड एक झंडा बनाता है
        private const val FLAG_CODES =
            "AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI " +
            "CV KH CM CA CF TD CL CN CO KM CG CD CR CI HR CU CY CZ DK DJ DM DO EC EG SV GQ ER EE " +
            "SZ ET FJ FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN HU IS IN ID IR IQ IE IL IT JM " +
            "JP JO KZ KE KI KP KR KW KG LA LV LB LS LR LY LI LT LU MG MW MY MV ML MT MH MR MU MX " +
            "FM MD MC MN ME MA MZ MM NA NR NP NL NZ NI NE NG MK NO OM PK PW PA PG PY PE PH PL PT " +
            "QA RO RU RW KN LC VC WS SM ST SA SN RS SC SL SG SK SI SB SO ZA SS ES LK SD SR SE CH " +
            "SY TW TJ TZ TH TL TG TO TT TN TR TM TV UG UA AE GB US UY UZ VU VA VE VN YE ZM ZW PS"
    }
}
