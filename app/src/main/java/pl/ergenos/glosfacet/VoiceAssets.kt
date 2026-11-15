// SPDX-License-Identifier: GPL-3.0-only
package pl.ergenos.glosfacet

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal object VoiceAssets {
    @Synchronized fun prepare(context: Context): File {
        val manifestBytes = context.assets.open("voice/manifest.json").use { it.readBytes() }
        val id = hex(MessageDigest.getInstance("SHA-256").digest(manifestBytes))
        val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))
        val root = File(context.filesDir, "voice-v4-$id")
        if (File(root, ".complete").readTextOrNull() == id) return root
        val required = manifest.keys().asSequence().sumOf { manifest.getJSONObject(it).getLong("bytes") }
        check(StatFs(context.filesDir.absolutePath).availableBytes > required + 64L * 1024 * 1024) {
            "Za mało wolnego miejsca na przygotowanie głosu"
        }
        val stage = File(context.filesDir, "voice-staging")
        if (stage.exists()) check(stage.deleteRecursively())
        check(stage.mkdirs())
        try {
            for (name in manifest.keys()) {
                check(!File(name).isAbsolute && name.split('/').all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".." } && name != ".complete")
                val record = manifest.getJSONObject(name)
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                context.assets.open("voice/$name").use { source ->
                    val destination = File(stage, name)
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { target ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            count += n
                            check(count <= record.getLong("bytes")) { "Nieprawidłowy rozmiar zasobów" }
                            digest.update(buffer, 0, n); target.write(buffer, 0, n)
                        }
                    }
                }
                check(count == record.getLong("bytes") && hex(digest.digest()) == record.getString("sha256")) {
                    "Nie można zweryfikować zasobów głosu"
                }
            }
            File(stage, ".complete").writeText(id)
            if (root.exists()) check(root.deleteRecursively())
            check(stage.renameTo(root)) { "Nie można przygotować głosu" }
            return root
        } catch (error: Throwable) {
            stage.deleteRecursively()
            throw error
        }
    }
    private fun File.readTextOrNull(): String? = if (isFile) readText() else null
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
