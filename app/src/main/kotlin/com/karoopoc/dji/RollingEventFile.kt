package com.karoopoc.dji

import java.io.File

/** Called only on the diagnostics worker. Two bounded UTF-8 files, no raw packets. */
internal class RollingEventFile(private val directory: File, private val limit: Long = 256 * 1024L) {
    fun append(line: String) {
        directory.mkdirs()
        val current = File(directory, "ride.log")
        val previous = File(directory, "ride.previous.log")
        val bytes = (line.replace('\n', ' ').replace('\r', ' ').take(400) + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= limit)
        if (current.length() + bytes.size > limit) {
            if (previous.exists() && !previous.delete()) return
            if (current.exists() && !current.renameTo(previous)) return
        }
        current.appendBytes(bytes)
    }
}
