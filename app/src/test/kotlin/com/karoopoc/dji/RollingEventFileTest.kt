package com.karoopoc.dji

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RollingEventFileTest {
    @Test fun rotatingLogIsBoundedAcrossRestartAndPreservesNewestEvents() {
        val directory = Files.createTempDirectory("ride-log-test").toFile()
        try {
            repeat(1000) { RollingEventFile(directory, 1024).append("event=TEST count=$it") }
            val files = directory.listFiles()!!.toList()
            assertEquals(2, files.size)
            assertTrue(files.all { it.length() <= 1024 })
            assertTrue(directory.resolve("ride.log").readText().contains("count=999"))
        } finally { directory.deleteRecursively() }
    }
    @Test fun longAndMultilineEventsCannotGrowUnbounded() {
        val directory = Files.createTempDirectory("ride-log-test").toFile()
        try {
            RollingEventFile(directory).append("line\n" + "x".repeat(10000))
            assertEquals(1, directory.resolve("ride.log").readLines().size)
            assertTrue(directory.resolve("ride.log").length() <= 401)
        } finally { directory.deleteRecursively() }
    }
}
