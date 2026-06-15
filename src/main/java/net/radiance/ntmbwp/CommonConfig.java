package net.radiance.ntmbwp;

import net.minecraftforge.common.ForgeConfigSpec;

public class CommonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Example setting
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;

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