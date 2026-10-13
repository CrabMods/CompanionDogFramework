package com.crabmods.companiondog.visualtest.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(InputConstants.class)
public abstract class IsolatedInputMixin {
    @Inject(method="grabOrReleaseMouse", at=@At("HEAD"), cancellable=true)
    private static void noNativeGrab(long window, int mode, double x, double y, CallbackInfo ci) { ci.cancel(); }
    @Inject(method="isKeyDown", at=@At("HEAD"), cancellable=true)
    private static void noNativePolling(long window, int key, CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(false); }
}
