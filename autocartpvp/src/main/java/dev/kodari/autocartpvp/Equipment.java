package dev.kodari.autocartpvp;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public record Equipment(int rail, int cart, int bow, int flint, int crossbow, int shield, boolean arrows) {
    /** Bow mode needs a Flame bow; crossbow mode needs flint and steel plus a crossbow (arrows can be in the crossbow). */
    public boolean ready(boolean crossbowMode) {
        if (rail < 0 || cart < 0) return false;
        return crossbowMode ? (flint >= 0 && crossbow >= 0) : (bow >= 0 && arrows);
    }

    private static boolean isArrow(ItemStack s) {
        return s.isOf(Items.ARROW) || s.isOf(Items.SPECTRAL_ARROW) || s.isOf(Items.TIPPED_ARROW);
    }

    public static Equipment scan(PlayerInventory inv, boolean searchMain) {
        int rail = -1, cart = -1, bow = -1, flint = -1, cb = -1, shield = -1;
        boolean cbCharged = false, arrows = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (isArrow(s)) arrows = true;
            if (shield < 0 && s.isOf(Items.SHIELD)) shield = i;
            if (i >= 9 && !searchMain) continue;
            if (rail < 0 && s.isOf(Items.RAIL)) rail = i;
            if (cart < 0 && s.isOf(Items.TNT_MINECART)) cart = i;
            if (flint < 0 && s.isOf(Items.FLINT_AND_STEEL)) flint = i;
            if (s.isOf(Items.CROSSBOW)) {
                boolean ch = CrossbowItem.isCharged(s);
                if (cb < 0 || (ch && !cbCharged)) { cb = i; cbCharged = ch; }
            }
            if (bow < 0 && s.isOf(Items.BOW)
                    && s.getEnchantments().getEnchantmentEntries().stream()
                    .anyMatch(e -> e.getKey().matchesKey(Enchantments.FLAME) && e.getIntValue() > 0)) bow = i;
        }
        if (isArrow(inv.getStack(40))) arrows = true;
        return new Equipment(rail, cart, bow, flint, cb, shield, arrows);
    }
}
