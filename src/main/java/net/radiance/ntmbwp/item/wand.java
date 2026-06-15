package net.radiance.ntmbwp.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class wand extends Item {


    private static final Queue<FillTask> FILL_TASKS = new LinkedList<>();

    public wand(Properties props) {
        super(props);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !FILL_TASKS.isEmpty()) {
            FillTask task = FILL_TASKS.peek();
            if (task.tick()) {
                FILL_TASKS.poll();
            }
        }
    }

    private static class FillTask {
        private final Level level;
        private final Iterator<BlockPos> positions;
        private final BlockState state;
        private final int blocksPerTick;

        FillTask(Level level, List<BlockPos> allPositions, BlockState state, int blocksPerTick) {
            this.level = level;
            this.positions = allPositions.iterator();
            this.state = state;
            this.blocksPerTick = blocksPerTick;
        }

        boolean tick() {
            int placed = 0;
            while (positions.hasNext() && placed < blocksPerTick) {
                BlockPos pos = positions.next();
                level.setBlock(pos, state, 3);
                placed++;
            }
            return !positions.hasNext();
        }
    }


    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Set positions with right click,").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Set block with shift-right click!").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Creative-only item").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("\"Destruction brings creation\"").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            tooltip.add(Component.literal("[SHIFT]").withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        var player = context.getPlayer();
        if (player == null || level.isClientSide) return InteractionResult.PASS;

        CompoundTag tag = context.getItemInHand().getOrCreateTag();


        if (player.isShiftKeyDown()) {
            BlockState clickedState = level.getBlockState(pos);
            Block clickedBlock = clickedState.getBlock();
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(clickedBlock);
            if (key != null) {
                tag.putString("targetBlock", key.toString());
                CompoundTag stateTag = new CompoundTag();
                for (Property<?> prop : clickedState.getProperties()) {
                    stateTag.putString(prop.getName(), clickedState.getValue(prop).toString());
                }
                tag.put("targetState", stateTag);
                player.displayClientMessage(Component.literal("Set block: " + key), false);
            }
            return InteractionResult.SUCCESS;
        }


        if (!tag.contains("startPos")) {
            tag.putLong("startPos", pos.asLong());
            player.displayClientMessage(Component.literal("Position set!"), false);
        } else {
            BlockPos startPos = BlockPos.of(tag.getLong("startPos"));
            scheduleFill(level, startPos, pos, tag);
            tag.remove("startPos");
            player.displayClientMessage(Component.literal("Selection Filled!"), false);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.MISS) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) {
        if (!level.isClientSide) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString("targetBlock", "minecraft:air");
            tag.remove("targetState");
            player.displayClientMessage(Component.literal("Set block: minecraft:air"), false);
        }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void scheduleFill(Level level, BlockPos pos1, BlockPos pos2, CompoundTag tag) {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        String blockId = tag.getString("targetBlock");
        Block targetBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (targetBlock == null) targetBlock = Blocks.STONE;

        BlockState stateToPlace = targetBlock.defaultBlockState();
        if (tag.contains("targetState")) {
            stateToPlace = reconstructState(targetBlock, tag.getCompound("targetState"));
        }

        List<BlockPos> allPositions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    allPositions.add(new BlockPos(x, y, z));
                }
            }
        }

        int blocksPerTick = net.radiance.ntmbwp.CommonConfig.BLOCKS_PER_TICK.get();
        FILL_TASKS.add(new FillTask(level, allPositions, stateToPlace, blocksPerTick));
    }

    private static BlockState reconstructState(Block baseBlock, CompoundTag stateTag) {
        BlockState state = baseBlock.defaultBlockState();
        for (String propName : stateTag.getAllKeys()) {
            Property<?> prop = state.getBlock().getStateDefinition().getProperty(propName);
            if (prop != null) {
                String value = stateTag.getString(propName);
                state = applyProperty(state, prop, value);
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<?> prop, String valueString) {
        for (T allowed : ((Property<T>) prop).getPossibleValues()) {
            if (allowed.toString().equals(valueString)) {
                return state.setValue((Property<T>) prop, allowed);
            }
        }
        return state;
    }
}