package dev.robin.hud.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    /** Sets the value without the vanilla range check (brightness above 100%). */
    @Accessor("value") void robinhud$setRaw(Object v);
}
