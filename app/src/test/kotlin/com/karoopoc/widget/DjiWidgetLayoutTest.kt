package com.karoopoc.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class DjiWidgetLayoutTest {
    @Test
    fun `classifies documented Karoo-style field sizes`() {
        assertEquals(DjiWidgetSize.LARGE, DjiWidgetLayout.sizeFor(480, 189, 60, 30))
        assertEquals(DjiWidgetSize.MEDIUM, DjiWidgetLayout.sizeFor(480, 154, 60, 20))
        assertEquals(DjiWidgetSize.SMALL, DjiWidgetLayout.sizeFor(480, 113, 60, 15))
        assertEquals(DjiWidgetSize.SMALL_HALF, DjiWidgetLayout.sizeFor(238, 113, 30, 15))
        assertEquals(DjiWidgetSize.FULL, DjiWidgetLayout.sizeFor(480, 420, 60, 60))
    }

    @Test
    fun `maps DJI video status values to readable labels`() {
        assertEquals("1080P", videoResolutionLabel(10))
        assertEquals("4K", videoResolutionLabel(16))
        assertEquals("2.7K 4:3", videoResolutionLabel(95))
        assertEquals("24", videoFpsLabel(1))
        assertEquals("60", videoFpsLabel(6))
        assertEquals("200", videoFpsLabel(19))
    }
}
