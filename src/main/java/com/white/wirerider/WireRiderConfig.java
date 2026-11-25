package com.white.wirerider;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WireRiderConfig {
    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    private WireRiderConfig() {}

    public static final class Common {
        public final ModConfigSpec.DoubleValue maxRideSpeed;
        public final ModConfigSpec.BooleanValue enableElectricHazards;
        public final ModConfigSpec.DoubleValue liveVoltageThreshold;
        public final ModConfigSpec.DoubleValue voltageDamageDivisor;
        public final ModConfigSpec.DoubleValue maxShockDamage;
        public final ModConfigSpec.DoubleValue groundShockDistance;
        public final ModConfigSpec.DoubleValue wireBurnTemperature;
        public final ModConfigSpec.DoubleValue wireCriticalTemperature;
        public final ModConfigSpec.BooleanValue enableStallArcHazard;
        public final ModConfigSpec.DoubleValue stallMinVoltage;
        public final ModConfigSpec.DoubleValue stallBuildupSeconds;

        Common(ModConfigSpec.Builder builder) {
            builder.push("physics");
            maxRideSpeed = builder
                .comment("Maximum zipline sliding speed (blocks/tick).")
                .defineInRange("maxRideSpeed", 2.1, 0.5, 5.0);
            builder.pop();

            builder.push("hazards");
            enableElectricHazards = builder
                .comment("Enable electrical ground-arc shock and wire overheat hazards on live bare wires.")
                .define("enableElectricHazards", true);
            liveVoltageThreshold = builder
                .comment("Minimum voltage (V) above which an uninsulated wire is considered live.")
                .defineInRange("liveVoltageThreshold", 50.0, 1.0, 10000.0);
            voltageDamageDivisor = builder
                .comment("Divisor applied to wire voltage to calculate shock damage (voltage / divisor).")
                .defineInRange("voltageDamageDivisor", 2500.0, 100.0, 50000.0);
            maxShockDamage = builder
                .comment("Maximum electric shock damage dealt in a single ground-arc hit.")
                .defineInRange("maxShockDamage", 24.0, 1.0, 100.0);
            groundShockDistance = builder
                .comment("Distance (blocks) to solid ground/water that triggers an electric shock while dry (extended in rain).")
                .defineInRange("groundShockDistance", 0.9, 0.3, 3.0);
            wireBurnTemperature = builder
                .comment("Wire temperature (°C) at which riding begins burning the player.")
                .defineInRange("wireBurnTemperature", 75.0, 35.0, 500.0);
            wireCriticalTemperature = builder
                .comment("Wire temperature (°C) at which the player is knocked off due to extreme heat.")
                .defineInRange("wireCriticalTemperature", 110.0, 50.0, 500.0);
            enableStallArcHazard = builder
                .comment("Shock and knock off players who stall/stop moving on live high-voltage bare wires.")
                .define("enableStallArcHazard", true);
            stallMinVoltage = builder
                .comment("Minimum voltage (V) on a live bare wire where stalling triggers arc buildup.")
                .defineInRange("stallMinVoltage", 1500.0, 50.0, 50000.0);
            stallBuildupSeconds = builder
                .comment("Seconds before stalling on a live high-voltage wire triggers an electric shock (faster in rain).")
                .defineInRange("stallBuildupSeconds", 10.0, 1.0, 60.0);
            builder.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.DoubleValue slideSoundVolume;
        public final ModConfigSpec.DoubleValue electricSoundVolume;
        public final ModConfigSpec.DoubleValue cameraRollStrength;
        public final ModConfigSpec.BooleanValue showHudOverlay;
        public final ModConfigSpec.BooleanValue enableParticles;
        public final ModConfigSpec.BooleanValue enableWireHighlight;

        Client(ModConfigSpec.Builder builder) {
            builder.push("audio");
            slideSoundVolume = builder
                .comment("Volume scale for the continuous zipline sliding sound (0.0 disables it).")
                .defineInRange("slideSoundVolume", 0.35, 0.0, 1.0);
            electricSoundVolume = builder
                .comment("Volume scale for live-wire crackle and arc warning sounds (0.0 disables them).")
                .defineInRange("electricSoundVolume", 0.35, 0.0, 1.0);
            builder.pop();

            builder.push("visuals");
            cameraRollStrength = builder
                .comment("Intensity of camera tilt during lane switches, jumps, drops, and slopes (0.0 disables tilt).")
                .defineInRange("cameraRollStrength", 1.0, 0.0, 2.0);
            showHudOverlay = builder
                .comment("Show compact red warning triangle next to the crosshair when riding live or hazardous wires.")
                .define("showHudOverlay", true);
            enableParticles = builder
                .comment("Spawn electric sparks, smoke, and leap particles while riding.")
                .define("enableParticles", true);
            enableWireHighlight = builder
                .comment("Show a Create-styled brass highlight & grab ring when aiming at a wire with a wrench.")
                .define("enableWireHighlight", true);
            builder.pop();
        }
    }
}
