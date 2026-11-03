package com.karoopoc.dji

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Explicit USB opt-in only. Never pass device addresses, raw frames, or GPS here. */
internal object RideDiagnostics {
    private val dropped = AtomicInteger()
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(128), { r -> Thread(r, "AeroCam-ride-log").apply { isDaemon = true } },
        { _, _ -> dropped.incrementAndGet() }).apply { allowCoreThreadTimeOut(true) }
    private var started = false // Worker-owned.
    fun event(context: Context, name: String, details: String = "") {
        val app = context.applicationContext
        val line = "${Instant.now()} elapsedMs=${SystemClock.elapsedRealtime()} event=$name ${details.take(240)}"
        worker.execute {
            try {
                val root = app.getExternalFilesDir(null) ?: return@execute
                val directory = File(root, "diagnostics")
                if (!File(directory, "enabled").exists()) { started = false; return@execute }
                val output = RollingEventFile(directory)
                if (!started) {
                    val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
                    output.append("${Instant.now()} event=LOG_START app=$version android=${Build.VERSION.RELEASE} build=${Build.VERSION.INCREMENTAL}")
                    started = true
                }
                val lost = dropped.getAndSet(0)
                if (lost > 0) output.append("${Instant.now()} event=LOG_DROPPED count=$lost")
                output.append(line)
            } catch (_: Exception) {
                // Storage unavailable/full or diagnostics failure must not affect camera control.
                dropped.incrementAndGet()
            }
        }
    }
}
