package net.radiance.ntmbwp;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;


    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue CLASSIC_MODE;

    static {
        BUILDER.push("NTM Construction Wand Settings");

        BLOCKS_PER_TICK = BUILDER
                .comment("Number of blocks placed per tick, keep it between 300 and 10000 for best results")
                .comment("Default Value: 10000")
                .defineInRange("blocksPerTick", 10000, 1, 100000);
        CLASSIC_MODE = BUILDER
                .comment("Classic mode disables additions added by the port, making the wand work exactly like it does in NTM")
                .comment("Default Value: false")
                .define("classicmode", false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}