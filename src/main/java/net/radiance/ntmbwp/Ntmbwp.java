package net.radiance.ntmbwp;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.radiance.ntmbwp.item.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

@Mod(Ntmbwp.MOD_ID)
@EventBusSubscriber
public class Ntmbwp {

    public static final String MOD_ID = "ntmbwp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ntmbwp(ModContainer container, IEventBus modEventBus) {
        container.registerConfig(ModConfig.Type.COMMON, net.radiance.ntmbwp.CommonConfig.SPEC);
        ModDataComponents.register(modEventBus);
        ModItems.register(container.getEventBus());
    }

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(Minecraft.getInstance().options.operatorItemsTab().get()){
            if(event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                event.accept(ModItems.WAND);
            }
        }
    }
}
