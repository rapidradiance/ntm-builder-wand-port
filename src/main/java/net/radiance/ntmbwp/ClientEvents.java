package net.radiance.ntmbwp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.radiance.ntmbwp.item.ModItems;

@EventBusSubscriber(value = Dist.CLIENT)
public class ClientEvents {
        @SubscribeEvent
        public static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (Minecraft.getInstance().options.operatorItemsTab().get()) {
                if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                    event.accept(ModItems.WAND);
                }
            }
    }
}
