package com.crabmods.companiondog.visualtest.mixin;
import com.mojang.blaze3d.platform.ClipboardManager;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClipboardManager.class)
public abstract class IsolatedClipboardMixin {
    @Inject(method="setClipboard", at=@At("HEAD"), cancellable=true)
    private void noClipboardWrite(long window, String text, CallbackInfo ci) { ci.cancel(); }
    @Inject(method="getClipboard", at=@At("HEAD"), cancellable=true)
    private void noClipboardRead(long window, GLFWErrorCallbackI error, CallbackInfoReturnable<String> ci) { ci.setReturnValue(""); }
}
