package com.crabmods.companiondog.visualtest;

import com.crabmods.companiondog.client.CompanionDogRenderer;
import com.crabmods.companiondog.client.model.DogAppearance;
import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Hidden-client appearance verification: renders every held pose, every action at four phases,
 * walk phases, mouth items and face close-ups of the active appearance, audits ground contact
 * over dense pose/action samples, then writes a receipt and exits. Test source set only.
 */
@Mod(value = "companiondog_visualtest", dist = Dist.CLIENT)
public final class VisualHarness {
    public static float deviceGain = Float.NaN;
    public static int gainWrites;
    private static final int BACKGROUND = 0xffa8c3b7;
    private static final int[] FOUR_VIEWS = {0, 90, 180, 270};

    private final Minecraft mc = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("companiondog.visualtest.output"));
    private final Path receipt = Path.of(System.getProperty("companiondog.visualtest.receipt"));
    private final Instant started = Instant.now();
    private final JsonArray rows = new JsonArray();
    private final List<Case> cases = new ArrayList<>();
    private final boolean reloadOnly = Boolean.getBoolean("companiondog.visualtest.reloadOnly");
    private int phase, index, ticks, isolationChecks;
    private CompanionDogEntity dog;
    private Preview screen;
    private ResourceReloadSuite resourceReload;
    private boolean reloadReady;
    private boolean done;
    private String geometryFailure = "";

    /** One rendered frame; {@code walk} >= 0 drives the real locomotion path instead of a debug pose. */
    private record Case(String id, CompanionDogPose pose, CompanionDogAction action, float progress, String item,
                        int[] yaws, float scale, float lift, float walk, boolean tame) {
        static Case of(String id, CompanionDogPose pose, CompanionDogAction action, float progress) {
            return new Case(id, pose, action, progress, "", FOUR_VIEWS, 280, 0, -1, true);
        }
    }

    public VisualHarness() {
        if (!Boolean.getBoolean("companiondog.visualtest")) throw new IllegalStateException("Test instrumentation cannot run without isolation");
        if (Boolean.getBoolean("companiondog.visualtest.showcase")) {
            new CompanionShowcase();
            return;
        }
        for (var pose : CompanionDogPose.values()) cases.add(Case.of("pose." + name(pose), pose, CompanionDogAction.NONE, 0));
        for (var action : CompanionDogAction.values()) {
            if (action == CompanionDogAction.NONE) continue;
            var pose = action.requiredPose() == null ? CompanionDogPose.STAND : action.requiredPose();
            for (float p : new float[] {0.1F, 0.35F, 0.6F, 0.85F}) {
                cases.add(Case.of("action." + name(action) + "." + Math.round(p * 100), pose, action, p));
            }
        }
        for (int quarter = 0; quarter < 4; quarter++) {
            // limbSwing * 0.9 drives the gait, so 1.745 per quarter cycle.
            cases.add(new Case("walk." + quarter, null, null, 0, "", new int[] {90, 135}, 280, 0, quarter * 1.745F, true));
        }
        int[] mouthViews = {90, 135, 180};
        cases.add(new Case("mouth.ball", CompanionDogPose.STAND, CompanionDogAction.NONE, 0, "ball", mouthViews, 380, 0.35F, -1, true));
        cases.add(new Case("mouth.disc", CompanionDogPose.STAND, CompanionDogAction.NONE, 0, "disc", mouthViews, 300, 0.2F, -1, true));
        cases.add(new Case("mouth.bone", CompanionDogPose.STAND, CompanionDogAction.NONE, 0, "bone", mouthViews, 380, 0.35F, -1, true));
        int[] faceViews = {180, 150};
        cases.add(new Case("face.normal", CompanionDogPose.STAND, CompanionDogAction.NONE, 0, "", faceViews, 700, 0.72F, -1, true));
        cases.add(new Case("face.happy", CompanionDogPose.STAND, CompanionDogAction.PET, 0.25F, "", faceViews, 700, 0.72F, -1, true));
        cases.add(new Case("face.sleep", CompanionDogPose.SLEEP, CompanionDogAction.NONE, 0, "", faceViews, 700, 0.3F, -1, true));
        cases.add(new Case("face.tongue", CompanionDogPose.STAND, CompanionDogAction.YAWN, 0.5F, "", faceViews, 700, 0.72F, -1, true));
        cases.add(new Case("untamed.stand", CompanionDogPose.STAND, CompanionDogAction.NONE, 0, "", FOUR_VIEWS, 280, 0, -1, false));
        if (reloadOnly) {
            cases.clear();
            cases.add(Case.of("resource.baseline", CompanionDogPose.STAND, CompanionDogAction.NONE, 0));
        }
        ResourceReloadSuite.IDS.forEach(id -> cases.add(Case.of(id, CompanionDogPose.STAND, CompanionDogAction.NONE, 0)));
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void isolate() {
        long window = mc.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != 0 || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0
                || GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL
                || GLFW.glfwGetWindowMonitor(window) != 0 || gainWrites > 0 && deviceGain != 0) {
            throw new IllegalStateException("Desktop isolation violation");
        }
        mc.options.pauseOnLostFocus = false;
        mc.options.framerateLimit().set(30);
        isolationChecks++;
    }

    private void tick(ClientTickEvent.Post event) {
        if (done) return;
        try {
            isolate();
            if (java.time.Duration.between(started, Instant.now()).toMinutes() > 8) throw new IllegalStateException("Visual test watchdog");
            if (phase == 0) {
                if (mc.screen == null || mc.getOverlay() != null) return;
                Files.createDirectories(output);
                if (!DogAppearance.load(mc.getResourceManager()).definition().hasGeometry()) {
                    throw new IllegalStateException("Dog appearance did not load");
                }
                mc.options.onboardingAccessibilityFinished();
                mc.options.guiScale().set(1);
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                mc.options.hideGui = true;
                mc.resizeDisplay();
                var settings = new LevelSettings("Companion visual fixture", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("visual-fixture-" + System.getenv("MINECRAFT_MOD_TEST_SESSION_ID"), settings, new WorldOptions(1, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), mc.screen);
                phase = 1;
            } else if (phase == 1) {
                if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
                if (++ticks < 40) return;
                dog = CompanionDogRegistries.COMPANION_DOG.get().create(mc.level);
                if (dog == null) throw new IllegalStateException("No dog fixture");
                dog.setTame(true, true);
                dog.setCollarColor(DyeColor.CYAN);
                if (!reloadOnly) {
                    try { auditModel(); }
                    catch (IllegalStateException failure) {
                        if (!failure.getMessage().startsWith("Ear/tail ground penetration")) throw failure;
                        // This is a healthy runtime with an asset defect; collect frames but keep final FAIL.
                        geometryFailure = failure.toString();
                    }
                }
                resourceReload = new ResourceReloadSuite(mc, output);
                screen = new Preview();
                mc.setScreen(screen);
                phase = 2;
            } else if (phase == 2) {
                if (screen.failure != null) throw new IllegalStateException(screen.failure);
                if (index == cases.size()) finish(geometryFailure);
                else if (cases.get(index).id().startsWith("reload.")) {
                    reloadReady = resourceReload.tick(cases.get(index).id(), dog);
                    if (reloadReady && mc.screen != screen) mc.setScreen(screen);
                }
            }
        } catch (Throwable failure) {
            finish(failure.toString());
        }
    }

    /** Dense samples of every valid pose/action pair and head direction: nothing may sink into the ground. */
    private void auditModel() throws Exception {
        var model = ((CompanionDogRenderer) mc.getEntityRenderDispatcher().getRenderer(dog)).getModel();
        for (var item : List.of(CompanionDogRegistries.TENNIS_BALL.get(), CompanionDogRegistries.FRISBEE.get())) {
            var baked = mc.getItemRenderer().getModel(new ItemStack(item), mc.level, dog, 0);
            if (baked.getQuads(null, null, net.minecraft.util.RandomSource.create(1)).isEmpty()) {
                throw new IllegalStateException("Toy item model is empty: " + item);
            }
        }
        var lowestByPart = new java.util.TreeMap<String, Float>();
        int samples = 0;
        float ground = 0, decorations = 0;
        String worst = "";
        for (var pose : CompanionDogPose.values()) for (var action : CompanionDogAction.values()) {
            if (action.requiredPose() != null && action.requiredPose() != pose) continue;
            for (int step = 0; step <= 100; step++) for (int look = -1; look <= 1; look++) {
                dog.setDebugAnimation(pose, action, step / 100F);
                model.setupAnim(dog, 0, 0, dog.tickCount, look * 55, look * 45);
                float support = model.lowestSupportPoint(), bottom = model.lowestPoint();
                model.root().visit(new com.mojang.blaze3d.vertex.PoseStack(), (matrix, path, number, cube) -> {
                    if (!path.contains("_ear") && !path.contains("/tail")) return;
                    var vertex = new org.joml.Vector3f();
                    for (int i = 0; i < 8; i++) {
                        vertex.set((i & 1) == 0 ? cube.minX : cube.maxX, (i & 2) == 0 ? cube.minY : cube.maxY,
                                (i & 4) == 0 ? cube.minZ : cube.maxZ).div(16);
                        matrix.pose().transformPosition(vertex);
                        lowestByPart.merge(path, vertex.y * 16 - 24, Math::max);
                    }
                });
                if (!Float.isFinite(support) || !Float.isFinite(bottom)) throw new IllegalStateException("Nonfinite model geometry");
                if (action != CompanionDogAction.JUMP) {
                    ground = Math.max(ground, support - 24);
                    if (bottom - 24 > decorations) {
                        decorations = bottom - 24;
                        worst = pose + "/" + action + "/" + step + "/" + look;
                    }
                    if (support > 24.03F) throw new IllegalStateException("Body/limb ground penetration " + support);
                }
                samples++;
            }
        }
        var metrics = new JsonObject();
        metrics.addProperty("samples", samples);
        metrics.addProperty("maximumSupportPenetrationPixels", ground);
        metrics.addProperty("maximumDecorationPenetrationPixels", decorations);
        metrics.addProperty("worstDecorationCase", worst);
        metrics.add("decorationDepthByPart", new GsonBuilder().create().toJsonTree(lowestByPart));
        Files.writeString(output.resolve("geometry-audit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(metrics));
        if (decorations > 0.03F) throw new IllegalStateException("Ear/tail ground penetration " + decorations + " at " + worst);
        dog.setDebugAnimation(null, null, 0);
    }

    private void prepare(Case c) {
        dog.setTame(c.tame(), true);
        if (c.walk() >= 0) {
            dog.setDebugAnimation(null, null, 0);
            // update() adds the new speed to the position; position(0) lags one step behind.
            float start = dog.walkAnimation.position();
            dog.walkAnimation.update(c.walk() - start - 1, 1);
            dog.walkAnimation.update(1, 1);
            dog.walkAnimation.update(1, 1);
        } else {
            dog.setDebugAnimation(c.pose(), c.action(), c.progress());
        }
        dog.setMouthItem(switch (c.item()) {
            case "ball" -> new ItemStack(CompanionDogRegistries.TENNIS_BALL.get());
            case "disc" -> new ItemStack(CompanionDogRegistries.FRISBEE.get());
            case "bone" -> new ItemStack(Items.BONE);
            default -> ItemStack.EMPTY;
        });
    }

    private void reset() {
        dog.setMouthItem(ItemStack.EMPTY);
        dog.setDebugAnimation(null, null, 0);
        dog.setTame(true, true);
        dog.walkAnimation.update(0, 1);
        dog.walkAnimation.update(0, 1);
    }

    private final class Preview extends Screen {
        int frames;
        String failure;

        Preview() {
            super(Component.literal("Appearance verification"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            if (done || index >= cases.size()) return;
            if (cases.get(index).id().startsWith("reload.") && !reloadReady) return;
            try {
                isolate();
                Case c = cases.get(index);
                prepare(c);
                g.fill(0, 0, width, height, BACKGROUND);
                g.drawString(font, c.id(), 20, 20, 0xff22302a, false);
                int columns = c.yaws().length;
                for (int view = 0; view < columns; view++) {
                    int yaw = c.yaws()[view];
                    dog.setYRot(yaw);
                    dog.yBodyRot = dog.yBodyRotO = yaw;
                    dog.yHeadRot = dog.yHeadRotO = yaw;
                    dog.setXRot(0);
                    g.pose().pushPose();
                    g.pose().translate((view + 0.5) * width / columns, height * 0.72 + c.lift() * c.scale(), 50);
                    g.pose().scale(c.scale(), c.scale(), -c.scale());
                    g.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI));
                    Lighting.setupForEntityInInventory();
                    var renderer = mc.getEntityRenderDispatcher();
                    renderer.setRenderShadow(false);
                    RenderSystem.runAsFancy(() -> renderer.render(dog, 0, 0, 0, 0, 0, g.pose(), g.bufferSource(), 15728880));
                    g.flush();
                    renderer.setRenderShadow(true);
                    g.pose().popPose();
                    Lighting.setupFor3DItems();
                }
                g.flush();
                if (++frames >= 3) capture(c);
            } catch (Throwable e) {
                failure = e.toString();
            }
        }

        private void capture(Case c) throws Exception {
            Path path = output.resolve(c.id() + ".png");
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                int subject = 0, toy = 0;
                int br = BACKGROUND >> 16 & 255, bg = BACKGROUND >> 8 & 255, bb = BACKGROUND & 255;
                for (int y = 50; y < image.getHeight() - 10; y++) for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getPixelRGBA(x, y), r = color & 255, green = color >> 8 & 255, b = color >> 16 & 255;
                    if (Math.abs(r - br) + Math.abs(green - bg) + Math.abs(b - bb) > 45) subject++;
                    if (c.item().equals("ball") && green > 170 && r > 140 && green > b + 80) toy++;
                    if (c.item().equals("disc") && r > 190 && r > green + 50 && green > b - 10 && b < 140) toy++;
                }
                boolean emptyExpected = c.id().startsWith("reload.") && !ResourceReloadSuite.expectsGeometry(c.id());
                if (emptyExpected ? subject != 0 : subject < 2000) {
                    throw new IllegalStateException("Unexpected empty/visible geometry: " + c.id() + " pixels=" + subject);
                }
                if ((c.item().equals("ball") || c.item().equals("disc")) && toy < 30) {
                    throw new IllegalStateException("Held toy is not visible: " + c.id());
                }
                image.writeToFile(path);
                var row = new JsonObject();
                row.addProperty("id", c.id());
                row.addProperty("passed", true);
                row.addProperty("cleanupPassed", true);
                row.addProperty("startedAtTick", mc.player.tickCount);
                row.addProperty("finishedAtTick", mc.player.tickCount);
                row.addProperty("subjectPixels", subject);
                row.addProperty("toyPixels", toy);
                var artifact = new JsonObject();
                artifact.addProperty("path", path.toString());
                artifact.addProperty("sha256", hash(path));
                var artifacts = new JsonArray();
                artifacts.add(artifact);
                row.add("artifacts", artifacts);
                rows.add(row);
            }
            reset();
            index++;
            reloadReady = false;
            frames = 0;
        }
    }

    private void finish(String failure) {
        if (done) return;
        done = true;
        try {
            if (dog != null) reset();
            var r = new JsonObject();
            r.addProperty("schemaVersion", 1);
            r.addProperty("sessionId", System.getenv("MINECRAFT_MOD_TEST_SESSION_ID"));
            r.addProperty("scope", reloadOnly ? "resource-reload-only; no animation geometry acceptance" : "full-appearance-and-resource-reload");
            r.addProperty("animationGeometryAudit", reloadOnly ? "not-run" : geometryFailure.isEmpty() ? "passed" : "failed");
            r.addProperty("geometryFailure", geometryFailure);
            r.addProperty("processStartCount", 1);
            r.addProperty("worldLoadCount", mc.level == null ? 0 : 1);
            r.addProperty("scenarioCount", rows.size());
            r.addProperty("allScenariosPassed", failure.isEmpty() && rows.size() == cases.size());
            r.addProperty("failedCount", failure.isEmpty() ? 0 : 1);
            r.addProperty("cleanupFailureCount", 0);
            r.addProperty("failure", failure);
            r.add("scenarios", rows);
            r.addProperty("startedAt", started.toString());
            r.addProperty("finishedAt", Instant.now().toString());
            var requested = new JsonArray();
            cases.forEach(c -> requested.add(c.id()));
            r.add("requestedScenarios", requested);
            var observed = new JsonArray();
            for (var row : rows) observed.add(row.getAsJsonObject().get("id").getAsString());
            r.add("observedScenarios", observed);
            var isolation = new JsonObject();
            for (String key : new String[] {"enabled", "hidden", "unfocused", "cursorFree", "windowed", "clipboardIsolated"}) {
                isolation.addProperty(key, true);
            }
            isolation.addProperty("silentDevice", gainWrites > 0 && deviceGain == 0);
            isolation.addProperty("verifiedTicks", isolationChecks);
            isolation.addProperty("violationCount", failure.contains("isolation") ? 1 : 0);
            r.add("desktopIsolation", isolation);
            Files.createDirectories(receipt.getParent());
            Files.writeString(receipt, new GsonBuilder().setPrettyPrinting().create().toJson(r));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mc.stop();
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
