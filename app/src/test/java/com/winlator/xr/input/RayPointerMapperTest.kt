package com.winlator.xr.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayPointerMapperTest {
    private val mapper = RayPointerMapperImpl()
    private val surface = VirtualScreenSurface.facingViewer(
        distanceMeters = 2f,
        widthMeters = 2f,
        heightMeters = 1f,
        pixelWidth = 2000,
        pixelHeight = 1000,
    )

    @Test
    fun centerRayMapsToTheCenterOfTheVirtualScreen() {
        val hit = mapper.map(
            ControllerPointerRay(PointerVector3(0f, 0f, 0f), PointerVector3(0f, 0f, -1f)),
            surface,
        )!!

        assertTrue(hit.isInsideSurface)
        assertEquals(0.5f, hit.normalizedX, 0.0001f)
        assertEquals(0.5f, hit.normalizedY, 0.0001f)
        assertEquals(1000, hit.pixelX)
        assertEquals(500, hit.pixelY)
    }

    @Test
    fun outOfBoundsRayIsClampedToTheNearestScreenEdge() {
        val hit = mapper.map(
            ControllerPointerRay(PointerVector3(0f, 0f, 0f), PointerVector3(2f, 0f, -2f)),
            surface,
        )!!

        assertFalse(hit.isInsideSurface)
        assertTrue(hit.wasClamped)
        assertEquals(1f, hit.normalizedX, 0.0001f)
        assertEquals(1999, hit.pixelX)
    }

    @Test
    fun parallelOrBehindScreenRaysAreRejected() {
        assertNull(
            mapper.map(
                ControllerPointerRay(PointerVector3(0f, 0f, 0f), PointerVector3(1f, 0f, 0f)),
                surface,
            ),
        )
        assertNull(
            mapper.map(
                ControllerPointerRay(PointerVector3(0f, 0f, 0f), PointerVector3(0f, 0f, 1f)),
                surface,
            ),
        )
    }

    @Test
    fun zeroYawAndPitchPointStraightForward() {
        val ray = ControllerPointerRay.fromYawPitch(PointerVector3(0f, 0f, 0f), 0f, 0f)
        val hit = mapper.map(ray, surface)!!

        assertEquals(0.5f, hit.normalizedX, 0.0001f)
        assertEquals(0.5f, hit.normalizedY, 0.0001f)
    }
}
