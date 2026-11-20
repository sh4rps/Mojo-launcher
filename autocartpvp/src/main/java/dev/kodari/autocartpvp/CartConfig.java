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
    /** Ticks to hold the bow before releasing. A bow released under ~3 ticks never fires; 5 leaves margin for server lag. */
    @SerialEntry public int drawTicks = 5;

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
