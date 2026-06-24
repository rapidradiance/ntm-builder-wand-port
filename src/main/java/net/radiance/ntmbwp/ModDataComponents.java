package net.radiance.ntmbwp;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.radiance.ntmbwp.item.Wand;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Ntmbwp.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GLOWING =
            DATA_COMPONENT_TYPES.register("glowing", () ->
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)           // save to disk
                            .networkSynchronized(ByteBufCodecs.BOOL) // sync to client
                            .build()
            );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Wand.WandState>> WAND_STATE =
            DATA_COMPONENT_TYPES.register("wand_state", () ->
                    DataComponentType.<Wand.WandState>builder()
                            .persistent(Wand.WandState.CODEC)
                            .networkSynchronized(Wand.WandState.STREAM_CODEC)
                            .build()
            );

    public static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}