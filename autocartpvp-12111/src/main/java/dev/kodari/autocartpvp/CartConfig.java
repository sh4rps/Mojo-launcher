package dev.kodari.autocartpvp;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CartConfig {
    public static final ConfigClassHandler<CartConfig> HANDLER = ConfigClassHandler.createBuilder(CartConfig.class)
            .id(Identifier.of("autocartpvp", "config"))
            .serializer(c -> GsonConfigSerializerBuilder.create(c)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("autocartpvp.json")).build())
            .build();

    @SerialEntry public boolean enabled = true;
    @SerialEntry public boolean autoDetect = true;
    @SerialEntry public float range = 4.0f;
    @SerialEntry public int delay = 1;
    @SerialEntry public boolean debug = false;
    @SerialEntry public boolean hud = true;
    /** Looking down: outline the blocks where the blast would hurt you little or not at all. */
    @SerialEntry public boolean showSafeSpots = true;
    /** Highest estimated blast damage (half-hearts, no armor) a block may cause and still count as safe. */
    @SerialEntry public int safeDamage = 4;
    /** Ticks to hold the bow before releasing. A bow released under ~3 ticks never fires; 5 leaves margin for server lag. */
    @SerialEntry public int drawTicks = 5;
    /** true: rail, cart, flint fire, crossbow shot through the fire in one tick. false: old Flame bow sequence. */
    @SerialEntry public boolean crossbowMode = true;
    /** In crossbow mode, load the crossbow first when it is not already charged. */
    @SerialEntry public boolean autoLoad = true;
    /** Raise a shield (moved to the off-hand) before the blast: raised shields block explosion damage. */
    @SerialEntry public boolean blastShield = false;
    /** After the shot, automatically step away from the cart (presses the right W/A/S/D keys, whatever way you face). */
    @SerialEntry public boolean retreat = true;
    @SerialEntry public int retreatTicks = 8;
    /** Keep shift held after the back-step until the TNT minecart has exploded, then stand up. */
    @SerialEntry public boolean sneakUntilBlast = true;
    /** Small instant push away from the cart, in hundredths of a block per tick of velocity. */
    @SerialEntry public int retreatPush = 25;
    /** Ticks the shield is raised before the shot (vanilla needs 5). */
    @SerialEntry public int shieldDelay = 7;
    /** Ticks the shield stays up after the shot, until the explosion has passed. */
    @SerialEntry public int shieldHold = 15;

    public static CartConfig get() { return HANDLER.instance(); }

    public static Screen screen(Screen parent) {
        CartConfig d = new CartConfig(), c = get();
        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Auto CartPvP"))
                .save(HANDLER::save)
                .category(ConfigCategory.createBuilder().name(Text.literal("General"))
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Enabled"))
                                .binding(d.enabled, () -> c.enabled, v -> c.enabled = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Crossbow + flint mode"))
                                .description(OptionDescription.of(Text.literal("On: place rail and TNT minecart, light fire in front of you with flint and steel, then shoot a crossbow through the fire, all in one tick. Off: old Flame bow sequence.")))
                                .binding(d.crossbowMode, () -> c.crossbowMode, v -> c.crossbowMode = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Auto-load crossbow"))
                                .description(OptionDescription.of(Text.literal("If the crossbow is not charged, charge it first, then run the one-tick sequence.")))
                                .binding(d.autoLoad, () -> c.autoLoad, v -> c.autoLoad = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Auto back-step after shot"))
                                .description(OptionDescription.of(Text.literal("After the crossbow shot you automatically move away from the cart (the right W/A/S/D keys are pressed for you), so the blast hurts less.")))
                                .binding(d.retreat, () -> c.retreat, v -> c.retreat = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Show safe blocks (look down)"))
                                .description(OptionDescription.of(Text.literal("When you look down, blocks where a cart blast would hurt you little or not at all are outlined in green.")))
                                .binding(d.showSafeSpots, () -> c.showSafeSpots, v -> c.showSafeSpots = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Safe = max damage (half-hearts)"))
                                .description(OptionDescription.of(Text.literal("0 = only blocks that do no damage at all. 4 = up to 2 hearts. Estimate without armor.")))
                                .binding(d.safeDamage, () -> c.safeDamage, v -> c.safeDamage = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0, 20).step(1)).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Sneak until blast"))
                                .description(OptionDescription.of(Text.literal("Shift is held during the back-step and until the TNT minecart has exploded; you stand up only after the blast.")))
                                .binding(d.sneakUntilBlast, () -> c.sneakUntilBlast, v -> c.sneakUntilBlast = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Back-step time (ticks)"))
                                .binding(d.retreatTicks, () -> c.retreatTicks, v -> c.retreatTicks = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(1, 20).step(1)).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Back-step push (0 = off)"))
                                .binding(d.retreatPush, () -> c.retreatPush, v -> c.retreatPush = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(0, 60).step(5)).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Blast shield (no explosion damage)"))
                                .description(OptionDescription.of(Text.literal("Needs a shield in your inventory. It is moved to the off-hand and raised before the shot, so the TNT minecart blast cannot hurt you (face the cart).")))
                                .binding(d.blastShield, () -> c.blastShield, v -> c.blastShield = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Shield raise time before shot (ticks)"))
                                .binding(d.shieldDelay, () -> c.shieldDelay, v -> c.shieldDelay = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(5, 20).step(1)).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Shield hold time after shot (ticks)"))
                                .binding(d.shieldHold, () -> c.shieldHold, v -> c.shieldHold = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(5, 40).step(1)).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Search main inventory"))
                                .description(OptionDescription.of(Text.literal("When disabled, required equipment must be in the hotbar. Arrows may be anywhere in the inventory.")))
                                .binding(d.autoDetect, () -> c.autoDetect, v -> c.autoDetect = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Float>createBuilder().name(Text.literal("Activation range"))
                                .description(OptionDescription.of(Text.literal("Maximum eye-to-target distance; server reach limits still apply.")))
                                .binding(d.range, () -> c.range, v -> c.range = v)
                                .controller(o -> FloatSliderControllerBuilder.create(o).range(1f, 6f).step(0.5f)).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Sequence delay (ticks)"))
                                .binding(d.delay, () -> c.delay, v -> c.delay = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(1, 20).step(1)).build())
                        .option(Option.<Integer>createBuilder().name(Text.literal("Bow draw time before shooting (ticks)"))
                                .description(OptionDescription.of(Text.literal("Lower = faster shot but weaker arrow. Below 3 the bow does not fire at all. Default 5.")))
                                .binding(d.drawTicks, () -> c.drawTicks, v -> c.drawTicks = v)
                                .controller(o -> IntegerSliderControllerBuilder.create(o).range(3, 20).step(1)).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("Debug messages"))
                                .binding(d.debug, () -> c.debug, v -> c.debug = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .option(Option.<Boolean>createBuilder().name(Text.literal("HUD indicator"))
                                .binding(d.hud, () -> c.hud, v -> c.hud = v)
                                .controller(TickBoxControllerBuilder::create).build())
                        .build())
                .build().generateScreen(parent);
    }
}
