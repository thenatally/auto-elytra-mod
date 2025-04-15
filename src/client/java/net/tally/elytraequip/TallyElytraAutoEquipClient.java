package net.tally.elytraequip;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TallyElytraAutoEquipClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    private boolean isProcessing = false;
    private long lastEquipAttempt = 0;
    private static final long EQUIP_COOLDOWN_MS = 1000;
    private boolean autoEquippedElytra = false;
    private boolean wasInAir = false;
    private ItemStack previousChestItem = ItemStack.EMPTY;
    private int previousElytraSlot = -1; // New variable to store the original Elytra slot

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !isProcessing) {

                if (System.currentTimeMillis() - lastEquipAttempt > EQUIP_COOLDOWN_MS) {
                    autoEquipElytra(client);
                }

                checkForLanding(client);
            }
        });
    }

    private void autoEquipElytra(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null)
            return;

        if (!player.isOnGround() && player.getVelocity().y < -0.6) {
            wasInAir = true;
            LOGGER.debug("Player is falling. Checking for safe landing...");
            if (isSafeLanding(player)) {
                LOGGER.debug("Safe landing detected. No action taken.");
                return;
            }

            ItemStack chestItem = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestItem.getItem() == Items.ELYTRA) {
                LOGGER.debug("Elytra already equipped. No action taken.");
                return;
            }

            previousChestItem = chestItem.copy();

            PlayerInventory inventory = player.getInventory();
            int elytraSlot = -1;

            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack.getItem() == Items.ELYTRA) {
                    elytraSlot = i;
                    break;
                }
            }

            if (elytraSlot != -1) {
                previousElytraSlot = elytraSlot; // Remember the original slot
                LOGGER.info("Found Elytra in inventory slot {}. Attempting to equip.", elytraSlot);
                isProcessing = true;
                lastEquipAttempt = System.currentTimeMillis();
                autoEquippedElytra = true;

                ClientPlayerInteractionManager interactionManager = client.interactionManager;

                int containerSlot = convertToContainerSlot(elytraSlot);
                int chestSlot = 6;

                client.execute(() -> {

                    interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, player);

                    interactionManager.clickSlot(0, chestSlot, 0, SlotActionType.PICKUP, player);

                    if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                        interactionManager.clickSlot(0, containerSlot, 0, SlotActionType.PICKUP, player);
                    }

                    client.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player, Mode.START_FALL_FLYING));

                    isProcessing = false;
                });
            }
        }
    }

    private void checkForLanding(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null)
            return;

        if (autoEquippedElytra && wasInAir && player.isOnGround() && !player.isFallFlying()) {
            LOGGER.info("Player has landed. Attempting to unequip Elytra.");
            wasInAir = false;
            isProcessing = true;

            PlayerInventory inventory = player.getInventory();
            int targetSlot = -1;
            
            // First try to return to the original Elytra slot
            if (previousElytraSlot != -1) {
                ItemStack targetStack = inventory.getStack(previousElytraSlot);
                if (targetStack.isEmpty()) {
                    targetSlot = previousElytraSlot;
                } else {
                    LOGGER.debug("Original slot {} is not empty. Searching for an empty slot...", previousElytraSlot);
                }
            }

            // Fallback: search for an empty slot in the main inventory
            if (targetSlot == -1) {
                for (int i = 9; i < 36; i++) {
                    if (inventory.getStack(i).isEmpty()) {
                        targetSlot = i;
                        break;
                    }
                }
                if (targetSlot == -1) {
                    for (int i = 0; i < 9; i++) {
                        if (inventory.getStack(i).isEmpty()) {
                            targetSlot = i;
                            break;
                        }
                    }
                }
            }

            if (targetSlot != -1) {
                int containerTargetSlot = convertToContainerSlot(targetSlot);
                int chestSlot = 6;

                ClientPlayerInteractionManager interactionManager = client.interactionManager;

                client.execute(() -> {

                    interactionManager.clickSlot(0, chestSlot, 0, SlotActionType.PICKUP, player);

                    interactionManager.clickSlot(0, containerTargetSlot, 0, SlotActionType.PICKUP, player);

                    if (!previousChestItem.isEmpty()) {

                        int prevItemSlot = -1;
                        for (int i = 0; i < inventory.size(); i++) {
                            ItemStack stack = inventory.getStack(i);
                            if (ItemStack.areEqual(stack, previousChestItem)) {
                                prevItemSlot = i;
                                break;
                            }
                        }

                        if (prevItemSlot != -1) {
                            int containerPrevItemSlot = convertToContainerSlot(prevItemSlot);

                            interactionManager.clickSlot(0, containerPrevItemSlot, 0, SlotActionType.PICKUP, player);

                            interactionManager.clickSlot(0, chestSlot, 0, SlotActionType.PICKUP, player);

                            if (!player.currentScreenHandler.getCursorStack().isEmpty()) {
                                interactionManager.clickSlot(0, containerPrevItemSlot, 0, SlotActionType.PICKUP,
                                        player);
                            }
                        }
                    }

                    autoEquippedElytra = false;
                    previousChestItem = ItemStack.EMPTY;
                    previousElytraSlot = -1; // Reset the stored slot
                    isProcessing = false;
                });
            } else {

                LOGGER.warn("No suitable inventory slot found to unequip Elytra.");
                player.sendMessage(Text.of("§c[ElytraEquip] §fCouldn't unequip Elytra: inventory full!"), false);

                autoEquippedElytra = false;
                previousChestItem = ItemStack.EMPTY;
                previousElytraSlot = -1;
                isProcessing = false;
            }
        }
    }

    private int convertToContainerSlot(int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return inventorySlot + 36;
        } else if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        } else if (inventorySlot >= 36 && inventorySlot <= 39) {
            return 8 - (inventorySlot - 36);
        } else if (inventorySlot == 40) {
            return 45;
        }
        return inventorySlot;
    }

    private boolean isSafeLanding(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        for (int i = 1; i <= 100; i++) {
            BlockState blockState = player.getWorld().getBlockState(pos.down(i));
            if (blockState.isAir())
                continue;
            if (isSafeBlock(blockState)) {
                LOGGER.debug("Safe block detected at distance {}: {}", i, blockState.getBlock().getTranslationKey());
                return true;
            } else {
                LOGGER.debug("Unsafe block detected at distance {}: {}", i, blockState.getBlock().getTranslationKey());
                return false;
            }
        }
        LOGGER.debug("No safe landing block detected within 100 blocks.");
        return false;
    }

    private boolean isSafeBlock(BlockState blockState) {
        return blockState.isOf(Blocks.WATER) ||
                blockState.isOf(Blocks.SLIME_BLOCK) ||
                blockState.isOf(Blocks.HAY_BLOCK) ||
                blockState.isOf(Blocks.COBWEB) ||
                blockState.isOf(Blocks.POWDER_SNOW);
    }
}
