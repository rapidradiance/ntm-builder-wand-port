package net.radiance.ntmbwp.item;

import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

import static net.radiance.ntmbwp.Ntmbwp.MOD_ID;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<Item> WAND = ITEMS.registerItem(
            "wand",
            (p) -> new Wand(p.stacksTo(1).rarity(Rarity.EPIC)),
            new Item.Properties());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
