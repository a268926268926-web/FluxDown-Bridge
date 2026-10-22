package com.galbrena.fluxdownbridge

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal object RootLauncher {
    private val LOCK = Any()
    private const val ACTIVITY = "com.fluxdown.app/.ExternalDownloadActivity"

    fun testRoot(): Result = run(listOf("su", "-c", "id"), timeoutSeconds = 20)

    fun openFluxDown(protocolUrl: String): Result {
        val safeUrl = protocolUrl.replace("'", "%27")
        val command = "am start --user current " +
            "-a android.intent.action.VIEW " +
            "-n $ACTIVITY " +
            "-d '$safeUrl'"
        return synchronized(LOCK) {
            run(listOf("su", "-c", command), timeoutSeconds = 20)
        }
    }

    private fun run(command: List<String>, timeoutSeconds: Int): Result {
        var process: Process? = null
        return try {
            val started = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process = started
            if (!started.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
                started.destroyForcibly()
                return Result(124, "root request timed out")
            }
            val output = started.inputStream.use { input ->
                ByteArrayOutputStream().also { bytes ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes.write(buffer, 0, count)
                    }
                }.toString(StandardCharsets.UTF_8.name())
            }.trim()
            val code = started.exitValue()
            Result(code, if (code == 0 && output.isEmpty()) "OK" else output)
        } catch (error: Exception) {
            process?.destroy()
            Result(-1, "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    data class Result(val code: Int, val output: String) {
        fun ok(): Boolean =
            code == 0 &&
                !output.contains("Permission denied") &&
                !output.contains("not allowed") &&
                !output.contains("does not exist") &&
                !output.contains("Error:") &&
                !output.contains("Exception")
    }
}
