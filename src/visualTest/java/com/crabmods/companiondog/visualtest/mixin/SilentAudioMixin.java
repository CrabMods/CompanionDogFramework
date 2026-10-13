package com.crabmods.companiondog.visualtest.mixin;
import com.crabmods.companiondog.visualtest.VisualHarness;
import com.mojang.blaze3d.audio.Listener;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Listener.class)
public abstract class SilentAudioMixin {
    @Redirect(method="setGain", at=@At(value="INVOKE", target="Lorg/lwjgl/openal/AL10;alListenerf(IF)V"))
    private void mutedListener(int parameter, float gain) {
        AL10.alListenerf(parameter, 0);
        VisualHarness.deviceGain = AL10.alGetListenerf(AL10.AL_GAIN);
        VisualHarness.gainWrites++;
    }
}
