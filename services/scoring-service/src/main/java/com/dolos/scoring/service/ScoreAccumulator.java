package com.dolos.scoring.service;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutable result a Drools session writes to. Each rule that fires calls {@link #add(int, String)}
 * to contribute its points and a human-readable reason; the engine reads the totals afterwards. The
 * score is capped at 100 so any combination of typologies stays in the 0–100 band.
 */
public final class ScoreAccumulator {

    private static final int MAX_SCORE = 100;

    private int score;
    private final List<String> reasons = new ArrayList<>();

    /** Records one rule hit: adds its points (capped at 100) and appends its reason. */
    public void add(int points, String reason) {
        score = Math.min(MAX_SCORE, score + points);
        reasons.add(reason);
    }

    public int getScore() {
        return score;
    }

    public List<String> getReasons() {
        return List.copyOf(reasons);
    }
}
