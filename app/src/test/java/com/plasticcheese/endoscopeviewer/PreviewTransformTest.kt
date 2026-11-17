package com.plasticcheese.endoscopeviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTransformTest {
    @Test
    fun landscapeFeedFitsPortraitScreenWithoutStretching() {
        val frame = Resolution(1920, 1080)

        assertEquals(Resolution(1080, 608), PreviewTransform().fit(frame, 1080, 1920))
    }

    @Test
    fun quarterTurnsSwapTheFeedAxesBeforeFitting() {
        val frame = Resolution(640, 480)

        for (turns in listOf(1, 3)) {
            val transform = PreviewTransform(quarterTurns = turns)
            assertEquals(Resolution(900, 1200), transform.fit(frame, 1920, 1200))
        }
        assertEquals(
            Resolution(1600, 1200),
            PreviewTransform(quarterTurns = 2).fit(frame, 1920, 1200),
        )
    }

    @Test
    fun zoomAndMirrorKeepTheSameViewport() {
        val frame = Resolution(320, 240)
        val transform = PreviewTransform(quarterTurns = 1, mirrored = true, zoom = 4f)

        assertEquals(Resolution(480, 640), transform.fit(frame, 480, 900))
    }
}
