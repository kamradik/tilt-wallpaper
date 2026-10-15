package dev.solotilt.personal;

import java.nio.ByteBuffer;

/**
 * CPU physics behind an effect (snow globe, sand, droplets). RenderLoop steps it once per frame with real
 * screen-space gravity and uploads its field to the shader as u_field, so the shader never loops over particles.
 */
interface Simulation {
    /** Field resolution: a portrait grid, row 0 at the bottom of the screen (GL texture order). */
    int W=72,H=160;
    /**
     * Advances the simulation.
     * @param dt seconds since the last step (clamped to 0.1 by the caller)
     * @param gx gravity along the screen's x axis (right), gy along its y axis (up); length 0 when the phone lies flat, 1 when upright
     * @param shake 0..1, how hard the phone is being shaken right now
     * @param touchX touch position in 0..1 screen space (y up), or NaN when nothing touches
     * @return true while anything still moves, so the renderer keeps scheduling frames
     */
    boolean step(float dt,float gx,float gy,float shake,float touchX,float touchY);
    /** Writes W*H RGBA bytes (meaning of each channel is the effect's own) into rgba, starting at position 0. */
    void fill(ByteBuffer rgba);
}
