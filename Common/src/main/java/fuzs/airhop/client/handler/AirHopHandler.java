package fuzs.airhop.client.handler;

import fuzs.airhop.AirHop;
import fuzs.airhop.capability.AirHopsCapability;
import fuzs.airhop.config.ServerConfig;
import fuzs.airhop.handler.PlayerFallHandler;
import fuzs.airhop.init.ModRegistry;
import fuzs.airhop.mixin.client.accessor.LivingEntityAccessor;
import fuzs.airhop.network.client.C2SAirHopMessage;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Optional;
import java.util.function.BiConsumer;

public class AirHopHandler {

    public void onPlayerTick$end(Player player) {
        if (player.getAbilities().flying) {
            // don't use an air hop immediately after stopping creative mode flight
            ((LivingEntityAccessor) player).setNoJumpDelay(10);
        } else if (((LivingEntityAccessor) player).getJumping() && ((LivingEntityAccessor) player).getNoJumpDelay() == 0 && this.attemptJump(player)) {
            // prevent accidental usage of air hops
            ((LivingEntityAccessor) player).setNoJumpDelay(10);
            // trigger jump on server
            AirHop.NETWORK.sendToServer(new C2SAirHopMessage());
        }
    }

    private boolean attemptJump(Player player) {
        if (this.canJump(player) && this.isSaturated(player)) {
            Optional<AirHopsCapability> optional = ModRegistry.AIR_HOPS_CAPABILITY.maybeGet(player);
            if (optional.isPresent()) {
                final AirHopsCapability capability = optional.get();
                if (capability.getAirHops() < getAllEnchantmentLevels(player.getArmorSlots(), ModRegistry.AIR_HOP_ENCHANTMENT.get())) {
                    player.jumpFromGround();
                    player.resetFallDistance();
                    capability.addAirHop();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canJump(Player player) {
        if (!player.isOnGround()) {
            if (!AirHop.CONFIG.get(ServerConfig.class).fallingOnly || PlayerFallHandler.getJumpHeight(player) / 2.0F < player.fallDistance) {
                if (!(player.isPassenger() || player.getAbilities().flying || player.onClimbable())) {
                    return !(player.isInWater() || player.isInLava());
                }
            }
        }
        return false;
    }

    private boolean isSaturated(Player player) {
        // air hopping always works in creative mode
        return player.getAbilities().mayfly || !AirHop.CONFIG.get(ServerConfig.class).disableOnHungry || player.getFoodData().getFoodLevel() > 6;
    }

    private static int getAllEnchantmentLevels(Iterable<ItemStack> armorItems, Enchantment enchantment) {
        MutableInt mutableint = new MutableInt();
        runIterationOnInventory((enchantment1, level) -> {
            if (enchantment1 == enchantment) {
                mutableint.add(level);
            }
        }, armorItems);
        return mutableint.intValue();
    }

    private static void runIterationOnItem(BiConsumer<Enchantment, Integer> enchantmentVisitor, ItemStack itemStack) {
        if (!itemStack.isEmpty()) {
            ListTag listTag = itemStack.getEnchantmentTags();
            for (int i = 0; i < listTag.size(); ++i) {
                CompoundTag compoundTag = listTag.getCompound(i);
                Registry.ENCHANTMENT
                        .getOptional(EnchantmentHelper.getEnchantmentId(compoundTag))
                        .ifPresent(enchantment -> enchantmentVisitor.accept(enchantment, EnchantmentHelper.getEnchantmentLevel(compoundTag)));
            }
        }
    }

    private static void runIterationOnInventory(BiConsumer<Enchantment, Integer> enchantmentVisitor, Iterable<ItemStack> iterable) {
        for (ItemStack itemStack : iterable) {
            runIterationOnItem(enchantmentVisitor, itemStack);
        }
    }
}
