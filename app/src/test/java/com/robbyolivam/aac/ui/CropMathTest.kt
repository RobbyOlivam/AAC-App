package com.robbyolivam.aac.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {
    @Test
    fun centeredCropOfLandscapePhotoIsMiddleSquare() {
        // 400×200 photo in a 100px square: the 200×200 middle.
        assertEquals(Triple(100, 0, 200), CropMath.cropSquare(400, 200, 100f, 1f, 0f, 0f))
    }

    @Test
    fun zoomAndPanPickTheMatchingSquare() {
        // At 2× the square shows 100×100 photo pixels; panning the photo right by the full
        // allowance shows its left edge.
        val (maxX, maxY) = CropMath.maxPan(400, 200, 100f, 2f)
        assertEquals(150f, maxX)
        assertEquals(50f, maxY)
        assertEquals(Triple(0, 50, 100), CropMath.cropSquare(400, 200, 100f, 2f, maxX, 0f))
        assertEquals(Triple(300, 100, 100), CropMath.cropSquare(400, 200, 100f, 2f, -maxX, -maxY))
    }

    @Test
    fun cropStaysInsideThePhoto() {
        val (left, top, side) = CropMath.cropSquare(300, 500, 120f, 1f, 9999f, -9999f)
        assertEquals(300, side)
        assertEquals(0, left)
        assertEquals(200, top)
    }
}
