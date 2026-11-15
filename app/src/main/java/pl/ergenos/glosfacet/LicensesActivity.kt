// SPDX-License-Identifier: GPL-3.0-only
package pl.ergenos.glosfacet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Offline legal notices, separate from the everyday communication controls. */
class LicensesActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val padding = (20*resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(padding,padding,padding,padding)
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16,24,22)); addView(column) }
        fun label(value: String, size: Float) = TextView(this).apply {
            text=value; textSize=size; setTextColor(Color.rgb(235,245,239)); setPadding(0,padding/2,0,padding/2)
        }
        fun action(value: String, run: () -> Unit) = Button(this).apply {
            text=value; isAllCaps=false; textSize=16f; minHeight=(56*resources.displayMetrics.density).toInt()
            setOnClickListener { run() }
        }
        column.addView(label("Licencje i źródła",26f))
        column.addView(label("OpenSpeech jest darmowy i działa bez internetu. Kod aplikacji jest dostępny na GPL-3.0. Model głosu oraz biblioteki zachowują opisane poniżej wolne licencje.",17f))
        column.addView(action("Kod źródłowy OpenSpeech") {
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/ErgenosSW/OpenSpeech")))
        })
        val entries = listOf(
            "Kod aplikacji: GPL-3.0" to "OpenSpeech-LICENSE",
            "Apache 2.0" to "Apache-2.0.txt",
            "Model głosu: CC BY-SA 4.0" to "Matcha-CC-BY-SA-4.0.txt",
            "Autorzy i pochodzenie głosu" to "Matcha-ATTRIBUTION.md",
            "Matcha-TTS: MIT" to "Matcha-TTS-MIT.txt",
            "HiFi-GAN: MIT" to "HiFi-GAN-MIT.txt",
            "Informacje o modelu" to "Matcha-MODEL_CARD.md",
            "eSpeak NG: GPL-3.0" to "espeak-ng-GPL-3.0.txt",
            "Unicode" to "ucd-Unicode-LICENSE.txt",
            "ONNX Runtime: MIT" to "onnxruntime-MIT.txt",
            "Składniki ONNX Runtime" to "onnxruntime-ThirdPartyNotices.txt",
            "AndroidX i Kotlin: informacje licencyjne" to "gradle-runtime-NOTICES.txt"
        )
        for ((title,file) in entries) column.addView(action(title) {
            val content = assets.open("licenses/$file").bufferedReader().use { it.readText() }
            val body = ScrollView(this).apply { addView(label(content,14f).apply { setTextIsSelectable(true) }) }
            AlertDialog.Builder(this).setTitle(title).setView(body).setPositiveButton("Zamknij",null).show()
        })
        column.addView(action("Wróć") { finish() })
        setContentView(scroll)
    }
}
