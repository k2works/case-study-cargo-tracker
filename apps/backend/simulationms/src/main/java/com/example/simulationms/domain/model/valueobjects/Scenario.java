package com.example.simulationms.domain.model.valueobjects;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 実行するシナリオ。工程の並びそのものである。
 *
 * <p>並びを持つのは、<strong>どこまで進んだか</strong>を工程の位置で言えるようにするため。
 */
public record Scenario(String id, List<ScenarioStep> steps) {

    public Scenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("シナリオ ID は必須です");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("シナリオには少なくとも 1 つの工程が要ります");
        }
        if (new LinkedHashSet<>(steps).size() != steps.size()) {
            throw new IllegalArgumentException("同じ工程を 2 度並べることはできません: " + id);
        }
        steps = List.copyOf(steps);
    }

    public static Scenario of(String id, List<ScenarioStep> steps) {
        return new Scenario(id, steps);
    }

    /** 一般貨物の標準輸送。予約から精算までの全工程（14）を通す。 */
    public static Scenario standardTransport() {
        return new Scenario("standard-transport", List.of(ScenarioStep.values()));
    }

    public boolean includes(ScenarioStep step) {
        return steps.contains(step);
    }

    public ScenarioStep last() {
        return steps.getLast();
    }
}
