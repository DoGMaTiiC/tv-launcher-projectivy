package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.dynamicanimation.animation.SpringFrames;

import org.junit.Test;

public class TileTiltTest {

    @Test
    public void focusScaleAndMaxTiltMatchTvOsSpec() {
        assertEquals(1.05f, TileTilt.FOCUS_SCALE, 0.001f);
        assertEquals(1.035f, TileTilt.BANNER_PARALLAX_SCALE, 0.001f);
        assertTrue(TileTilt.MAX_TILT_DEG >= 2f && TileTilt.MAX_TILT_DEG <= 4f);
    }

    @Test
    public void rotationYReflectsHorizontalMoveAndClamps() {
        assertEquals(0f, TileTilt.rotationYForDx(0f, 320), 0.001f);
        assertEquals(0f, TileTilt.rotationYForDx(320f, 0), 0.001f);

        float right = TileTilt.rotationYForDx(320f, 320);
        float left = TileTilt.rotationYForDx(-320f, 320);
        assertTrue(right > 0f && right <= TileTilt.MAX_TILT_DEG);
        assertEquals(-right, left, 0.001f);

        float hugeJump = TileTilt.rotationYForDx(5000f, 320);
        assertEquals(TileTilt.MAX_TILT_DEG, hugeJump, 0.001f);
        assertEquals(-TileTilt.MAX_TILT_DEG, TileTilt.rotationYForDx(-5000f, 320), 0.001f);
    }

    @Test
    public void rotationXReflectsVerticalMoveAndClamps() {
        assertEquals(0f, TileTilt.rotationXForDy(0f, 180), 0.001f);
        assertEquals(0f, TileTilt.rotationXForDy(180f, 0), 0.001f);

        float down = TileTilt.rotationXForDy(180f, 180);
        float up = TileTilt.rotationXForDy(-180f, 180);
        assertTrue(down < 0f && down >= -TileTilt.MAX_TILT_DEG);
        assertEquals(-down, up, 0.001f);

        assertEquals(-TileTilt.MAX_TILT_DEG, TileTilt.rotationXForDy(5000f, 180), 0.001f);
        assertEquals(TileTilt.MAX_TILT_DEG, TileTilt.rotationXForDy(-5000f, 180), 0.001f);
    }

    @Test
    public void specularShiftStaysBoundedAndZeroAtRest() {
        assertEquals(0f, TileTilt.specularShift(0f, 0f), 0.001f);
        float high = TileTilt.specularShift(-10f, 10f);
        float low = TileTilt.specularShift(10f, -10f);
        assertEquals(1f, high, 0.001f);
        assertEquals(-1f, low, 0.001f);
    }

    @Test
    public void tiltSpringSettlesWithinBudget() {
        int normalFrames = SpringFrames.toSettle(TileTilt.TILT_STIFFNESS, TileTilt.TILT_DAMPING, 0.1f, TileTilt.MAX_TILT_DEG);
        assertTrue("normal tilt spring took " + normalFrames + " frames", normalFrames <= 30);

        int reducedFrames = SpringFrames.toSettle(Motion.REDUCED_STIFFNESS, 1f, 0.1f, TileTilt.MAX_TILT_DEG);
        assertTrue("reduced tilt spring took " + reducedFrames + " frames", reducedFrames <= 6);
    }
}
