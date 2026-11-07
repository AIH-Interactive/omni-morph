package org.figuramc.figura.animation;

import top.aihmc.omm.preview.PreviewLua;

/** Original time-controller bookkeeping driven by the configured simulation clock. */
public final class TimeController {
    private final PreviewLua owner;
    private double lastTime, time, pauseTime;

    public TimeController(PreviewLua owner) {
        this.owner = owner;
    }

    public void init() {
        pauseTime = 0;
        lastTime = time = owner.animationClock();
    }

    public void tick() {
        lastTime = time;
        // A newly started input at the target frame must not run backwards during seek replay.
        time = Math.max(time, owner.animationClock());
    }

    public void reset() {
        lastTime = time = pauseTime = 0;
    }

    public void pause() {
        lastTime = time;
        pauseTime = owner.animationClock();
    }

    public void resume() {
        double difference = owner.animationClock() - pauseTime;
        lastTime += difference;
        time += difference;
    }

    public float getDiff() {
        return (float) (time - lastTime);
    }
}
