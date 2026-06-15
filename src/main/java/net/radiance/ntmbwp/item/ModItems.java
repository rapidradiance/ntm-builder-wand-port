package net.radiance.ntmbwp.item;

import net.minecraft.world.item.Rarity;
import net.radiance.ntmbwp.Ntmbwp;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Ntmbwp.MOD_ID);

    public static final RegistryObject<Item> WAND = ITEMS.register("wand",
            () -> new wand(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
