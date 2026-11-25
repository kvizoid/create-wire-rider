package com.white.wirerider.compat;

public record WireStatusSnapshot(
    boolean energized,
    float voltage,
    float current,
    float temperature
) {}
