package dev.robin.robinium.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    /** Sets the value without the vanilla range check, so 33-64 chunks is possible. */
    @Accessor("value") void robinium$setRaw(Object v);
}
