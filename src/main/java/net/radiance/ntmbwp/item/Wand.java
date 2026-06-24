package net.radiance.ntmbwp.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.radiance.ntmbwp.CommonConfig;
import net.radiance.ntmbwp.ModDataComponents;
import net.radiance.ntmbwp.Ntmbwp;

import java.util.*;

import static net.radiance.ntmbwp.Ntmbwp.MOD_ID;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class Wand extends Item {

    // Record wandstate in a data component, ive probably done lots wrong here but it works so...
    // if it tells you to turn this into its own file, its lying that breaks everything and i have no idea why just leave it like this please
    public record WandState(Optional<BlockPos> startPos, Block targetBlock, BlockState targetState) {

        public static final Codec<WandState> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BlockPos.CODEC
                                .optionalFieldOf("start_pos")
                                .forGetter(WandState::startPos),
                        BuiltInRegistries.BLOCK.byNameCodec()
                                .fieldOf("target_block")
                                .forGetter(WandState::targetBlock),
                        BlockState.CODEC
                                .fieldOf("target_state")
                                .forGetter(WandState::targetState)
                ).apply(instance, WandState::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, WandState> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.optional(BlockPos.STREAM_CODEC.cast()),              WandState::startPos,
                        ByteBufCodecs.registry(Registries.BLOCK),                          WandState::targetBlock,
                        ByteBufCodecs.VAR_INT.map(Block::stateById, Block::getId).cast(),  WandState::targetState,
                        WandState::new
                );

        public static WandState of(Block block, BlockState state) {
            return new WandState(Optional.empty(), block, state);
        }

        public WandState withStartPos(BlockPos pos) {
            return new WandState(Optional.of(pos), this.targetBlock(), this.targetState());
        }

        public WandState clearStartPos() {
            return new WandState(Optional.empty(), this.targetBlock(), this.targetState());
        }

        public boolean hasStartPos() {
            return startPos.isPresent();
        }
    }

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
        boolean classic = CommonConfig.CLASSIC_MODE.get();
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("Set positions with right click,").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Set block with shift-right click!").withStyle(ChatFormatting.GRAY));
            if (!classic){tooltip.add(Component.literal("Reset Selection by left clicking in air").withStyle(ChatFormatting.GRAY));}
        } else {
            tooltip.add(Component.literal("Creative-only item").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("\"Destruction brings creation\"").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            tooltip.add(Component.literal("[SHIFT]").withStyle(ChatFormatting.YELLOW));
        }
    }




    // useOn (right-click on block) 

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // check classic mode setting
        boolean classic = CommonConfig.CLASSIC_MODE.get();
        Level  level  = context.getLevel();
        BlockPos pos  = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || level.isClientSide) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        WandState currentstate = stack.get(ModDataComponents.WAND_STATE);

        if (player.isShiftKeyDown()) {
            BlockState clickedState = level.getBlockState(pos);
            stack.set(ModDataComponents.WAND_STATE, WandState.of(clickedState.getBlock(), clickedState));

            // 1.21.1 registry lookup
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(clickedState.getBlock());
            player.displayClientMessage(
                    Component.literal("Set block: " + key), false);
            return InteractionResult.SUCCESS;
        }

        // First click will store the start pos and the second click will fill the selected areo
        if (currentstate != null && !currentstate.hasStartPos()) {
            stack.set(ModDataComponents.WAND_STATE, currentstate.withStartPos(pos));
            if (!classic) { stack.set(ModDataComponents.GLOWING, true); }
            player.displayClientMessage(Component.literal("Position set!"), false);
        }
        if (currentstate != null && currentstate.hasStartPos()) {
            BlockPos startPos = currentstate.startPos().get();
            BlockState target = currentstate.targetState();
            scheduleFill(level, startPos, pos, target);
            stack.set(ModDataComponents.WAND_STATE, currentstate.clearStartPos());
            if (!classic) { stack.set(ModDataComponents.GLOWING, false); }
            player.displayClientMessage(Component.literal("Selection Filled!"), false);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // use (right-click air, shift = set air as target) 

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        if (player.isSecondaryUseActive())

        if (hit.getType() != HitResult.Type.MISS) return InteractionResultHolder.pass(stack);

        if (player.isShiftKeyDown() && !level.isClientSide) {
            stack.set(ModDataComponents.WAND_STATE, WandState.of(Blocks.AIR, Blocks.AIR.defaultBlockState()));
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

    // left click stuff ig
    public void onLeftClick(Player player, Level level) {
        if (level.isClientSide()) return;
        // check classic mode setting
        boolean classic = CommonConfig.CLASSIC_MODE.get();
        if (classic) return;
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        WandState currentstate = stack.get(ModDataComponents.WAND_STATE);
        if (!currentstate.hasStartPos()) return; // makes sure it only works if the start pos is actually set
        stack.set(ModDataComponents.WAND_STATE, currentstate.clearStartPos());
        stack.set(ModDataComponents.GLOWING, false);
        player.displayClientMessage(Component.literal("Selection Reset!"), false);
    }

}
// ya so for left click, its a bitch, fuck you minecrarft, neoforge, java, etc
// also yes, sending a whole ass packet is needed, because left click empty is fucking special
@EventBusSubscriber
 class ItemEvents {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                WandLeftClickPayload.TYPE,
                WandLeftClickPayload.STREAM_CODEC,
                WandLeftClickPayload::handle
        );
    }
    public record WandLeftClickPayload() implements CustomPacketPayload {
        public static final Type<WandLeftClickPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Ntmbwp.MOD_ID, "wand_left_click"));
        public static final StreamCodec<ByteBuf, WandLeftClickPayload> STREAM_CODEC =
                StreamCodec.unit(new WandLeftClickPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void handle(WandLeftClickPayload payload, IPayloadContext context) {
            Player player = context.player();
            if (player.getMainHandItem().getItem() instanceof Wand item)
                item.onLeftClick(player, player.level());
        }
    }

    private static void tryDelegate(Player player) {
        if (player.getMainHandItem().getItem() instanceof Wand item)
            item.onLeftClick(player, player.level());
    }

    //@SubscribeEvent
    //public static void onBlock(PlayerInteractEvent.LeftClickBlock e) { tryDelegate(e.getEntity()); }

    @SubscribeEvent
    public static void onEmpty(PlayerInteractEvent.LeftClickEmpty e) {
        if (e.getEntity().level().isClientSide()) {
            PacketDistributor.sendToServer(new WandLeftClickPayload());
        }
    }
 }