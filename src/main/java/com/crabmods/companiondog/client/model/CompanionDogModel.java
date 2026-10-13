package com.crabmods.companiondog.client.model;

import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Procedural animation for CompanionDog. Every frame starts from the rest pose, blends the held poses
 * by their smoothed weights, adds locomotion and idle life, then layers the current one-shot
 * action on top. All action curves start and end at zero so they blend in and out cleanly.
 */
public class CompanionDogModel extends HierarchicalModel<CompanionDogEntity> {
    private static final float PI = (float) Math.PI;

    private final ModelPart root;
    private final float motionScale;
    final ModelPart body;
    final ModelPart head;
    final ModelPart jaw;
    private final ModelPart tongue;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart rightEar;
    private final ModelPart rightEarTip;
    private final ModelPart leftEar;
    private final ModelPart leftEarTip;
    private final ModelPart rightFrontLeg;
    private final ModelPart rightFrontPaw;
    private final ModelPart leftFrontLeg;
    private final ModelPart leftFrontPaw;
    private final ModelPart rightHindLeg;
    private final ModelPart rightHindPaw;
    private final ModelPart leftHindLeg;
    private final ModelPart leftHindPaw;

    public CompanionDogModel(ModelPart top) {
        this(top, 1.0F);
    }

    public CompanionDogModel(ModelPart top, float motionScale) {
        this.motionScale = motionScale;
        this.root = top.getChild("root");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.tongue = jaw.getChild("tongue");
        this.tail = body.getChild("tail");
        this.tailTip = tail.getChild("tail_tip");
        this.rightEar = head.getChild("right_ear");
        this.rightEarTip = rightEar.getChild("right_ear_tip");
        this.leftEar = head.getChild("left_ear");
        this.leftEarTip = leftEar.getChild("left_ear_tip");
        this.rightFrontLeg = body.getChild("right_front_leg");
        this.rightFrontPaw = rightFrontLeg.getChild("right_front_paw");
        this.leftFrontLeg = body.getChild("left_front_leg");
        this.leftFrontPaw = leftFrontLeg.getChild("left_front_paw");
        this.rightHindLeg = body.getChild("right_hind_leg");
        this.rightHindPaw = rightHindLeg.getChild("right_hind_paw");
        this.leftHindLeg = body.getChild("left_hind_leg");
        this.leftHindPaw = leftHindLeg.getChild("left_hind_paw");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    /** Walks the transform chain down to the jaw, for the held-item layer. */
    public void translateToMouth(com.mojang.blaze3d.vertex.PoseStack poseStack) {
        root.translateAndRotate(poseStack);
        body.translateAndRotate(poseStack);
        head.translateAndRotate(poseStack);
        jaw.translateAndRotate(poseStack);
    }

    @Override
    public void setupAnim(CompanionDogEntity dog, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float partial = Mth.clamp(ageInTicks - dog.tickCount, 0.0F, 1.0F);
        float sit = dog.poseWeight(CompanionDogPose.SIT, partial);
        float lie = dog.poseWeight(CompanionDogPose.LIE, partial);
        float sleep = dog.poseWeight(CompanionDogPose.SLEEP, partial);
        float belly = dog.poseWeight(CompanionDogPose.BELLY, partial);
        float stand = Mth.clamp(1.0F - sit - lie - sleep - belly, 0.0F, 1.0F);

        applySit(sit);
        applyLie(lie);
        applySleep(sleep);
        applyBelly(belly);

        CompanionDogAction action = dog.getClientAction(partial);
        float progress = dog.getClientActionProgress(partial);
        boolean frozen = dog.hasDebugAnimation();
        float age = frozen ? 0.0F : ageInTicks;

        float walk = frozen ? 0.0F : Math.min(limbSwingAmount, 1.0F) * stand;
        applyWalk(limbSwing, walk);

        float look = stand + sit + lie * 0.8F + belly * 0.3F;
        head.yRot += Mth.clamp(netHeadYaw, -55.0F, 55.0F) * Mth.DEG_TO_RAD * look * 0.9F;
        head.xRot += Mth.clamp(headPitch, -35.0F, 45.0F) * Mth.DEG_TO_RAD * look * 0.8F;

        applyIdle(dog, age, walk, sleep, belly);
        tongue.visible = dog.hasMood(CompanionDogEntity.MOOD_PANTING) || belly > 0.5F;
        if (action != CompanionDogAction.NONE) {
            applyAction(action, progress, progress * action.durationTicks());
        }
        if (dog.getMouthItem().is(CompanionDogRegistries.TENNIS_BALL.get())) {
            float rest = jaw.getInitialPose().xRot;
            jaw.xRot = rest + Mth.clamp(jaw.xRot - rest, 0.35F, 0.45F);
            tongue.visible = false;
        } else if (dog.getMouthItem().is(CompanionDogRegistries.FRISBEE.get())) {
            jaw.xRot = jaw.getInitialPose().xRot + 0.15F;
            tongue.visible = false;
        }
        if (motionScale != 1.0F) {
            root.getAllParts().forEach(part -> {
                var rest = part.getInitialPose();
                part.x = rest.x + (part.x - rest.x) * motionScale;
                part.y = rest.y + (part.y - rest.y) * motionScale;
                part.z = rest.z + (part.z - rest.z) * motionScale;
            });
        }
        // Derive ground contact from the supplied geometry, including bent poses.
        if (sit + lie + sleep + belly > 0.01F || action == CompanionDogAction.ROLL_OVER) {
            root.y += 24.0F - lowestSupportPoint();
        } else if (action != CompanionDogAction.JUMP) {
            root.y -= Math.max(0.0F, lowestSupportPoint() - 24.0F);
        }
    }

    public float lowestPoint() {
        return lowestPoint(path -> true);
    }

    /** Decoration cannot be the only support for a seated body. */
    public float lowestSupportPoint() {
        return lowestPoint(path -> !path.contains("/tail") && !path.contains("_ear")
                && !path.endsWith("/tongue"));
    }

    private float lowestPoint(java.util.function.Predicate<String> include) {
        float[] bottom = {-Float.MAX_VALUE};
        org.joml.Vector3f vertex = new org.joml.Vector3f();
        root.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
            if (!include.test(path) || (path.endsWith("/tongue") && !tongue.visible)) return;
            for (int corner = 0; corner < 8; corner++) {
                vertex.set((corner & 1) == 0 ? cube.minX : cube.maxX,
                        (corner & 2) == 0 ? cube.minY : cube.maxY,
                        (corner & 4) == 0 ? cube.minZ : cube.maxZ).div(16.0F);
                pose.pose().transformPosition(vertex);
                bottom[0] = Math.max(bottom[0], vertex.y * 16.0F);
            }
        });
        return bottom[0] == -Float.MAX_VALUE ? 24.0F : bottom[0];
    }

    // ------------------------------------------------------------------ held poses

    private void applySit(float w) {
        if (w <= 0.0F) return;
        body.y += 3.4F * w;
        body.xRot -= 0.785F * w;
        head.xRot += 0.62F * w;
        rightFrontLeg.xRot += 0.785F * w;
        leftFrontLeg.xRot += 0.785F * w;
        rightHindLeg.xRot -= 0.5F * w;
        leftHindLeg.xRot -= 0.5F * w;
        rightHindLeg.zRot += 0.14F * w;
        leftHindLeg.zRot -= 0.14F * w;
        rightHindPaw.xRot -= 0.3F * w;
        leftHindPaw.xRot -= 0.3F * w;
        tail.xRot += 0.8F * w;
    }

    private void applyLie(float w) {
        if (w <= 0.0F) return;
        body.y += 6.5F * w;
        head.xRot -= 0.05F * w;
        rightFrontLeg.xRot -= 1.45F * w;
        leftFrontLeg.xRot -= 1.45F * w;
        rightFrontLeg.zRot += 0.05F * w;
        leftFrontLeg.zRot -= 0.05F * w;
        rightHindLeg.xRot -= 1.3F * w;
        leftHindLeg.xRot -= 1.3F * w;
        rightHindLeg.zRot += 0.4F * w;
        leftHindLeg.zRot -= 0.4F * w;
        rightHindPaw.xRot += 2.4F * w;
        leftHindPaw.xRot += 2.4F * w;
        tail.xRot -= 1.3F * w;
    }

    private void applySleep(float w) {
        if (w <= 0.0F) return;
        applyLie(w);
        body.y += 0.4F * w;
        body.zRot += 0.3F * w;
        head.y += 2.4F * w;
        head.z -= 0.4F * w;
        head.xRot += 0.45F * w;
        head.yRot += 0.55F * w;
        head.zRot += 0.25F * w;
        rightEarTip.xRot -= 0.15F * w;
        leftEarTip.xRot -= 0.15F * w;
        tail.yRot -= 1.0F * w;
        tailTip.yRot -= 0.7F * w;
        rightFrontLeg.yRot += 0.25F * w;
        leftFrontLeg.yRot += 0.25F * w;
    }

    private void applyBelly(float w) {
        if (w <= 0.0F) return;
        body.y += 6.0F * w;
        body.zRot += PI * w;
        head.zRot -= 2.1F * w;
        head.xRot += 0.2F * w;
        rightFrontLeg.xRot -= 0.5F * w;
        leftFrontLeg.xRot -= 0.5F * w;
        rightFrontPaw.xRot += 1.3F * w;
        leftFrontPaw.xRot += 1.3F * w;
        rightHindLeg.xRot -= 0.4F * w;
        leftHindLeg.xRot -= 0.4F * w;
        rightHindLeg.zRot += 0.45F * w;
        leftHindLeg.zRot -= 0.45F * w;
        rightHindPaw.xRot += 1.0F * w;
        leftHindPaw.xRot += 1.0F * w;
        tail.xRot -= 1.0F * w;
    }

    // ------------------------------------------------------------------ locomotion and idle life

    private void applyWalk(float limbSwing, float amount) {
        if (amount <= 0.001F) return;
        float s = limbSwing * 0.9F;
        float a = 1.05F * amount;
        rightFrontLeg.xRot += Mth.cos(s) * a;
        leftFrontLeg.xRot += Mth.cos(s + PI) * a;
        rightHindLeg.xRot += Mth.cos(s + PI) * a;
        leftHindLeg.xRot += Mth.cos(s) * a;
        rightFrontPaw.xRot += Math.max(0.0F, Mth.sin(s)) * 0.7F * amount;
        leftFrontPaw.xRot += Math.max(0.0F, Mth.sin(s + PI)) * 0.7F * amount;
        rightHindPaw.xRot -= Math.max(0.0F, Mth.sin(s + PI)) * 0.45F * amount;
        leftHindPaw.xRot -= Math.max(0.0F, Mth.sin(s)) * 0.45F * amount;
        body.y -= Math.abs(Mth.cos(s)) * 0.45F * amount;
        head.xRot += Mth.cos(s * 2.0F) * 0.05F * amount;
        rightEarTip.xRot += Mth.sin(s * 2.0F) * 0.25F * amount;
        leftEarTip.xRot += Mth.sin(s * 2.0F + 0.4F) * 0.25F * amount;
        tail.yRot += Mth.sin(s) * 0.2F * amount;
    }

    private void applyIdle(CompanionDogEntity dog, float age, float walk, float sleep, float belly) {
        boolean happy = dog.hasMood(CompanionDogEntity.MOOD_HAPPY) || dog.hasMood(CompanionDogEntity.MOOD_BEGGING);
        boolean panting = dog.hasMood(CompanionDogEntity.MOOD_PANTING);
        boolean alert = dog.hasMood(CompanionDogEntity.MOOD_ALERT) || dog.hasMood(CompanionDogEntity.MOOD_BEGGING);
        float awake = 1.0F - sleep;

        float wagSpeed = happy ? 0.95F : 0.25F;
        float wagAmp = (happy ? 0.55F : 0.16F) * awake + 0.04F * sleep;
        tail.yRot += Mth.sin(age * wagSpeed) * wagAmp;
        tailTip.yRot += Mth.sin(age * wagSpeed - 0.9F) * wagAmp * 0.7F;
        if (happy) {
            tail.xRot += 0.25F * (1.0F - belly) * awake;
        }

        float breathe = sleep > 0.5F ? Mth.sin(age * 0.07F) * 0.18F : Mth.sin(age * 0.11F) * 0.1F;
        if (panting) {
            breathe = Mth.sin(age * 0.9F) * 0.12F;
            jaw.xRot += (0.32F + Mth.sin(age * 0.9F) * 0.07F) * awake;
        }
        body.y += breathe;

        rightEar.zRot += Mth.sin(age * 0.05F) * 0.03F;
        leftEar.zRot -= Mth.sin(age * 0.05F + 1.0F) * 0.03F;
        if (alert) {
            rightEarTip.xRot += 0.45F;
            leftEarTip.xRot += 0.45F;
            rightEar.xRot += 0.1F;
            leftEar.xRot += 0.1F;
        }
        if (dog.hasMood(CompanionDogEntity.MOOD_BEGGING)) {
            head.zRot += 0.28F * (1.0F - walk);
        }
    }

    // ------------------------------------------------------------------ one-shot actions

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Fades in over {@code in} and out over {@code out} (fractions of the action). */
    private static float window(float p, float in, float out) {
        return Math.min(ease(p / in), ease((1.0F - p) / out));
    }

    /** A smooth hump that is non-zero only between a and b. */
    private static float bump(float p, float a, float b) {
        if (p <= a || p >= b) return 0.0F;
        return Mth.sin((p - a) / (b - a) * PI);
    }

    private void earsBack(float amount) {
        rightEar.xRot -= 0.5F * amount;
        leftEar.xRot -= 0.5F * amount;
        rightEarTip.xRot -= 0.2F * amount;
        leftEarTip.xRot -= 0.2F * amount;
    }

    private void earsUp(float amount) {
        rightEarTip.xRot += 0.45F * amount;
        leftEarTip.xRot += 0.45F * amount;
    }

    private void applyAction(CompanionDogAction action, float p, float t) {
        switch (action) {
            case PET -> {
                float e = window(p, 0.15F, 0.2F);
                head.xRot -= 0.3F * e;
                head.zRot += Mth.sin(t * 0.35F) * 0.22F * e;
                body.zRot += Mth.sin(t * 0.3F) * 0.05F * e;
                earsBack(e);
                float lick = bump(p, 0.55F, 0.95F);
                jaw.xRot += 0.35F * lick;
                tongue.visible |= lick > 0.1F;
                tail.yRot += Mth.sin(t * 1.1F) * 0.3F * e;
            }
            case PAW -> {
                float lift = window(p, 0.2F, 0.2F);
                rightFrontLeg.xRot -= (1.25F + Mth.sin(t * 0.7F) * 0.12F) * lift;
                rightFrontPaw.xRot += 0.5F * lift;
                body.zRot -= 0.08F * lift;
                head.zRot += 0.2F * lift;
                head.xRot -= 0.1F * lift;
            }
            case BEG -> {
                float up = window(p, 0.2F, 0.15F);
                float paddle = Mth.sin(t * 0.6F) * 0.18F * up;
                body.xRot -= 0.55F * up;
                body.y -= 1.0F * up;
                head.xRot += 0.5F * up;
                rightFrontLeg.xRot -= 0.9F * up + paddle;
                leftFrontLeg.xRot -= 0.9F * up - paddle;
                rightFrontPaw.xRot += 1.1F * up;
                leftFrontPaw.xRot += 1.1F * up;
                rightHindLeg.xRot += 0.35F * up;
                leftHindLeg.xRot += 0.35F * up;
                tail.xRot += 0.6F * up;
                earsUp(up);
            }
            case ROLL_OVER -> {
                float down = window(p, 0.18F, 0.18F);
                float roll = ease((p - 0.2F) / 0.6F);
                body.y += 6.3F * down;
                body.zRot += 2.0F * PI * roll;
                head.xRot += 0.6F * down;
                float tuck = down * (0.6F + 0.4F * bump(p, 0.2F, 0.8F));
                rightFrontLeg.xRot -= 1.2F * tuck;
                leftFrontLeg.xRot -= 1.2F * tuck;
                rightFrontPaw.xRot += 1.1F * tuck;
                leftFrontPaw.xRot += 1.1F * tuck;
                rightHindLeg.xRot -= 1.0F * tuck;
                leftHindLeg.xRot -= 1.0F * tuck;
                rightHindPaw.xRot += 1.2F * tuck;
                leftHindPaw.xRot += 1.2F * tuck;
                tail.xRot -= 1.0F * down;
            }
            case SPIN -> {
                float e = window(p, 0.1F, 0.2F);
                root.yRot += 2.0F * PI * ease(p);
                body.zRot += 0.12F * e;
                head.yRot += 0.5F * e;
                tail.yRot -= 0.5F * e;
                body.y -= Math.abs(Mth.sin(p * PI * 4.0F)) * 0.9F * e;
                rightFrontLeg.xRot += Mth.sin(t * 0.9F) * 0.5F * e;
                leftFrontLeg.xRot -= Mth.sin(t * 0.9F) * 0.5F * e;
                rightHindLeg.xRot -= Mth.sin(t * 0.9F) * 0.4F * e;
                leftHindLeg.xRot += Mth.sin(t * 0.9F) * 0.4F * e;
            }
            case PLAY_BOW -> {
                float e = window(p, 0.2F, 0.25F);
                body.xRot += 0.45F * e;
                body.y += 1.8F * e;
                body.yRot += Mth.sin(t * 0.8F) * 0.08F * e;
                rightFrontLeg.xRot -= 1.35F * e;
                leftFrontLeg.xRot -= 1.35F * e;
                rightFrontPaw.xRot += 0.9F * e;
                leftFrontPaw.xRot += 0.9F * e;
                rightHindLeg.xRot -= 0.45F * e;
                leftHindLeg.xRot -= 0.45F * e;
                head.xRot -= 0.75F * e;
                tail.xRot += 0.5F * e;
                tail.yRot += Mth.sin(t * 1.2F) * 0.4F * e;
                jaw.xRot += 0.45F * bump(p, 0.3F, 0.45F);
                earsUp(e);
            }
            case JUMP -> {
                float j = bump(p, 0.05F, 0.9F);
                rightFrontLeg.xRot -= 0.9F * j;
                leftFrontLeg.xRot -= 0.9F * j;
                rightFrontPaw.xRot += 0.6F * j;
                leftFrontPaw.xRot += 0.6F * j;
                rightHindLeg.xRot += 0.8F * j;
                leftHindLeg.xRot += 0.8F * j;
                body.xRot -= 0.25F * bump(p, 0.05F, 0.5F) - 0.2F * bump(p, 0.5F, 0.95F);
                rightEarTip.xRot += 0.6F * j;
                leftEarTip.xRot += 0.6F * j;
                tail.xRot += 0.3F * j;
            }
            case SHAKE -> {
                float e = window(p, 0.1F, 0.15F);
                float f = t * 2.4F;
                body.zRot += Mth.sin(f) * 0.3F * e;
                head.zRot += Mth.sin(f - 0.7F) * 0.45F * e;
                rightEar.zRot += Mth.sin(f - 1.2F) * 0.7F * e;
                leftEar.zRot += Mth.sin(f - 1.2F) * 0.7F * e;
                rightEarTip.xRot += Mth.sin(f - 1.5F) * 0.4F * e;
                leftEarTip.xRot += Mth.sin(f - 1.5F) * 0.4F * e;
                tail.yRot += Mth.sin(f - 1.5F) * 0.6F * e;
                rightFrontLeg.zRot += 0.1F * e;
                leftFrontLeg.zRot -= 0.1F * e;
            }
            case BARK -> {
                float b = Math.max(bump(p, 0.05F, 0.42F), bump(p, 0.5F, 0.92F));
                jaw.xRot += 0.55F * b;
                head.xRot -= 0.12F * b;
                body.z -= 0.3F * b;
                earsUp(b);
            }
            case HEAD_TILT -> {
                float e = window(p, 0.2F, 0.2F);
                head.zRot += 0.45F * bump(p, 0.05F, 0.55F) - 0.35F * bump(p, 0.5F, 0.95F);
                head.xRot -= 0.1F * e;
                earsUp(e);
            }
            case SNIFF -> {
                float e = window(p, 0.2F, 0.2F);
                body.xRot += 0.12F * e;
                rightFrontLeg.xRot -= 0.12F * e;
                leftFrontLeg.xRot -= 0.12F * e;
                rightHindLeg.xRot -= 0.12F * e;
                leftHindLeg.xRot -= 0.12F * e;
                head.y += 1.0F * e;
                head.z -= 0.5F * e;
                head.xRot += 0.85F * e + Mth.sin(t * 1.8F) * 0.03F * e;
                head.yRot += Mth.sin(t * 0.25F) * 0.35F * e;
            }
            case DIG -> {
                float e = window(p, 0.12F, 0.15F);
                float f = t * 1.5F;
                body.xRot += 0.3F * e;
                body.y += 0.8F * e;
                head.xRot += 0.45F * e;
                rightFrontLeg.xRot += e * (-0.55F + Mth.sin(f) * 0.65F);
                leftFrontLeg.xRot += e * (-0.55F + Mth.sin(f + PI) * 0.65F);
                rightFrontPaw.xRot += e * (0.4F + 0.4F * Mth.sin(f));
                leftFrontPaw.xRot += e * (0.4F + 0.4F * Mth.sin(f + PI));
                rightHindLeg.xRot -= 0.3F * e;
                leftHindLeg.xRot -= 0.3F * e;
                tail.xRot += 0.35F * e;
                tail.yRot += Mth.sin(t * 1.1F) * 0.35F * e;
            }
            case YAWN -> {
                float y = bump(p, 0.1F, 0.85F);
                jaw.xRot += 0.85F * y;
                head.xRot -= 0.5F * y;
                earsBack(0.7F * y);
                tongue.visible |= y > 0.3F;
                tongue.xRot -= 0.3F * y;
            }
            case SCRATCH -> {
                float e = window(p, 0.15F, 0.15F);
                rightHindLeg.xRot -= 0.9F * e - Mth.sin(t * 2.6F) * 0.28F * e;
                rightHindLeg.zRot += 0.55F * e;
                rightHindPaw.xRot -= 0.4F * e;
                body.zRot -= 0.12F * e;
                head.zRot -= 0.45F * e;
                head.yRot -= 0.3F * e;
            }
            case EAT -> {
                float e = window(p, 0.12F, 0.15F);
                float chew = Mth.sin(t * 1.6F);
                head.xRot += 0.75F * e + chew * 0.06F * e;
                head.y += 0.8F * e;
                jaw.xRot += e * (0.18F + 0.18F * chew);
            }
            case BELLY_RUB -> {
                float e = window(p, 0.1F, 0.15F);
                rightHindLeg.xRot += Mth.sin(t * 2.2F) * 0.55F * e;
                leftHindLeg.xRot += 0.2F * e;
                rightFrontLeg.xRot += Mth.sin(t * 0.9F) * 0.25F * e;
                leftFrontLeg.xRot += Mth.sin(t * 0.9F + 1.2F) * 0.25F * e;
                body.zRot += Mth.sin(t * 0.35F) * 0.12F * e;
                head.xRot += Mth.sin(t * 0.5F) * 0.1F * e;
                jaw.xRot += 0.3F * e;
                tongue.visible = true;
            }
            case STRETCH -> {
                float a = bump(p, 0.05F, 0.55F);
                float b = bump(p, 0.5F, 0.97F);
                body.xRot += 0.5F * a - 0.15F * b;
                body.y += 2.2F * a;
                body.z -= 1.0F * b;
                rightFrontLeg.xRot -= 1.35F * a - 0.25F * b;
                leftFrontLeg.xRot -= 1.35F * a - 0.25F * b;
                rightFrontPaw.xRot += 0.2F * a;
                leftFrontPaw.xRot += 0.2F * a;
                rightHindLeg.xRot += -0.5F * a + 0.75F * b;
                leftHindLeg.xRot += -0.5F * a + 0.75F * b;
                head.xRot -= 0.55F * a + 0.2F * b;
                tail.xRot += 0.4F * a;
                jaw.xRot += 0.5F * bump(p, 0.15F, 0.45F);
            }
            case HOWL -> {
                float e = window(p, 0.15F, 0.15F);
                head.xRot -= 1.0F * e;
                head.zRot += Mth.sin(t * 1.2F) * 0.03F * e;
                jaw.xRot += 0.5F * window(p, 0.2F, 0.15F);
                body.xRot -= 0.1F * e;
                earsBack(0.7F * e);
            }
            default -> {
            }
        }
    }
}
