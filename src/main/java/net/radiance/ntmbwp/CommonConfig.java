package net.radiance.ntmbwp;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // Example setting
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;

    static {
        BUILDER.push("NTM Construction Wand Settings");

        BLOCKS_PER_TICK = BUILDER
                .comment("Number of blocks placed per tick, keep it between 300 and 1000 for best results")
                .comment("Default Value: 1000")
                .defineInRange("blocksPerTick", 1000, 1, 100000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}