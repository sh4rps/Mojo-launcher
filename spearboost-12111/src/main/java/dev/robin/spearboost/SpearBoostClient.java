package dev.robin.spearboost;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Press O: swap the best spear into your hand, jab (lunge dash), swap back. */
public class SpearBoostClient implements ClientModInitializer {
    private enum Stage { IDLE, ATTACK, RESTORE }
    private Stage stage = Stage.IDLE;
    private int origSlot, spearInv = -1; // spearInv >= 9: spear came from the main inventory (swapped into the held slot)
    private int wait;

    @Override public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spearboost.boost", GLFW.GLFW_KEY_O, KeyBinding.Category.create(Identifier.of("spearboost", "controls"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) if (mc.currentScreen == null) start(mc);
            tick(mc);
        });
    }

    private static boolean isSpear(ItemStack s) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith("_spear");
    }

    private static boolean hasLunge(ItemStack s) {
        return s.getEnchantments().getEnchantmentEntries().stream()
                .anyMatch(e -> e.getKey().getIdAsString().endsWith("lunge") && e.getIntValue() > 0);
    }

    private static int findSpear(PlayerInventory inv) {
        int any = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!isSpear(s)) continue;
            if (hasLunge(s)) return i;
            if (any < 0) any = i;
        }
        return any;
    }

    private void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[SpearBoost] " + s), true);
    }

    private void start(MinecraftClient mc) {
        if (stage != Stage.IDLE || mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        PlayerInventory inv = mc.player.getInventory();
        int s = findSpear(inv);
        if (s < 0) { msg(mc, "No spear in inventory"); return; }
        origSlot = inv.getSelectedSlot();
        if (s < 9) {
            spearInv = -1;
            inv.setSelectedSlot(s);
        } else {
            spearInv = s;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, s, origSlot, SlotActionType.SWAP, mc.player);
        }
        stage = Stage.ATTACK;
        wait = 1; // let the client sync the new slot to the server first
    }

    private void tick(MinecraftClient mc) {
        if (stage == Stage.IDLE) return;
        if (mc.player == null || mc.interactionManager == null) { stage = Stage.IDLE; return; }
        if (wait-- > 0) return;
        switch (stage) {
            case ATTACK -> {
                // queue one attack click; it is processed at the start of the next tick with the spear already selected
                KeyBinding atk = mc.options.attackKey;
                KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(atk.getBoundKeyTranslationKey()));
                stage = Stage.RESTORE;
                wait = 2;
            }
            case RESTORE -> {
                PlayerInventory inv = mc.player.getInventory();
                if (spearInv >= 9 && mc.player.currentScreenHandler == mc.player.playerScreenHandler)
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, spearInv, origSlot, SlotActionType.SWAP, mc.player);
                inv.setSelectedSlot(origSlot);
                spearInv = -1;
                stage = Stage.IDLE;
            }
            default -> { }
        }
    }
}
