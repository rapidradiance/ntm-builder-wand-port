package net.radiance.ntmbwp.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.radiance.ntmbwp.ModDataComponents;

import java.util.*;

import static net.radiance.ntmbwp.Ntmbwp.MOD_ID;

// Per-player wand state (replaces NBT on the stack) 
record WandState(BlockPos startPos, Block targetBlock, BlockState targetState) {
    // startPos may be null (not yet set)
}

@SuppressWarnings("removal")
@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Wand extends Item {

    // Keyed by player UUID
    private static final Map<UUID, BlockPos>   START_POSITIONS = new HashMap<>();
    private static final Map<UUID, BlockState> TARGET_STATES   = new HashMap<>();

    private static final Queue<FillTask> FILL_TASKS = new LinkedList<>();

    public Wand(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Show enchant glow when true
        return stack.getOrDefault(ModDataComponents.GLOWING, false);
    }

    // Tick handler 

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {   // 1.21 splits into Pre/Post
        if (!FILL_TASKS.isEmpty()) {
            FillTask task = FILL_TASKS.peek();
            if (task.tick()) {
                FILL_TASKS.poll();
            }
        }
    }

    // FillTask (unchanged logic, just moved inside) 

    private static class FillTask {
        private final Level level;
        private final Iterator<BlockPos> positions;
        private final BlockState state;
        private final int blocksPerTick;

        FillTask(Level level, List<BlockPos> allPositions, BlockState state, int blocksPerTick) {
            this.level     = level;
            this.positions = allPositions.iterator();
            this.state     = state;
            this.blocksPerTick = blocksPerTick;
        }

        boolean tick() {
            int placed = 0;
            while (positions.hasNext() && placed < blocksPerTick) {
                level.setBlock(positions.next(), state, 3);
                placed++;
            }
            return !positions.hasNext();
        }
    }

    // Tooltip 

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Set positions with right click,").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Set block with shift-right click!").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Creative-only item").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("\"Destruction brings creation\"").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            tooltip.add(Component.literal("[SHIFT]").withStyle(ChatFormatting.YELLOW));
        }
    }

    // useOn (right-click on block) 

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level  level  = context.getLevel();
        BlockPos pos  = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || level.isClientSide) return InteractionResult.PASS;

        UUID uid = player.getUUID();

        if (player.isShiftKeyDown()) {
            // Store clicked block's state as the fill target
            BlockState clicked = level.getBlockState(pos);
            TARGET_STATES.put(uid, clicked);

            // 1.21.1 registry lookup
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(clicked.getBlock());
            player.displayClientMessage(
                    Component.literal("Set block: " + key), false);
            return InteractionResult.SUCCESS;
        }

        // First click → store start pos; second click → fill
        if (!START_POSITIONS.containsKey(uid)) {
            START_POSITIONS.put(uid, pos);
            player.displayClientMessage(Component.literal("Position set!"), false);
        } else {
            BlockPos startPos = START_POSITIONS.remove(uid);
            BlockState target = TARGET_STATES.getOrDefault(uid, Blocks.STONE.defaultBlockState());
            scheduleFill(level, startPos, pos, target);
            player.displayClientMessage(Component.literal("Selection Filled!"), false);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // use (right-click air, shift = set air as target) 

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        if (!level.isClientSide() && !player.isShiftKeyDown() && hit.getType() != HitResult.Type.MISS) {
            boolean currentlyGlowing = stack.getOrDefault(ModDataComponents.GLOWING, false);
            stack.set(ModDataComponents.GLOWING, !currentlyGlowing);
        }

        if (hit.getType() != HitResult.Type.MISS) return InteractionResultHolder.pass(stack);

        if (player.isShiftKeyDown() && !level.isClientSide) {
            TARGET_STATES.put(player.getUUID(), Blocks.AIR.defaultBlockState());
            player.displayClientMessage(Component.literal("Set block: minecraft:air"), false);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // Fill scheduling 

    private static void scheduleFill(Level level, BlockPos pos1, BlockPos pos2, BlockState stateToPlace) {
        int minX = Math.min(pos1.getX(), pos2.getX()), maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY()), maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ()), maxZ = Math.max(pos1.getZ(), pos2.getZ());

        List<BlockPos> allPositions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    allPositions.add(new BlockPos(x, y, z));

        int blocksPerTick = net.radiance.ntmbwp.CommonConfig.BLOCKS_PER_TICK.get();
        FILL_TASKS.add(new FillTask(level, allPositions, stateToPlace, blocksPerTick));
    }
}