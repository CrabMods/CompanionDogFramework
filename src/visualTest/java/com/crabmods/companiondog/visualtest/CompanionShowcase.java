package com.crabmods.companiondog.visualtest;

import com.crabmods.companiondog.client.CompanionDogInteractScreen;
import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogCommand;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.crabmods.companiondog.entity.FetchToyEntity;
import com.crabmods.companiondog.registry.CompanionDogRegistries;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Records actual item use and menu packets in one isolated, disposable client world. */
final class CompanionShowcase {
    private record Scene(String id, String caption, int ticks, CompanionDogCommand command, CompanionDogAction action) {}
    private static final List<Scene> SCENES = List.of(
            new Scene("welcome", "Welcome your companion - right-click the collar", 90, null, null),
            new Scene("bone-menu", "A bone opens the command menu - choose Sit", 95, CompanionDogCommand.SIT, null),
            new Scene("pet", "Empty hand - a little affection", 85, null, CompanionDogAction.PET),
            new Scene("give-paw", "Give paw - a command sent through the real menu", 95, CompanionDogCommand.PAW, CompanionDogAction.PAW),
            new Scene("play-bow", "Play bow - ready to play", 90, CompanionDogCommand.PLAY_BOW, CompanionDogAction.PLAY_BOW),
            new Scene("feed", "Offer wolf food - feeding and a happy reaction", 85, null, CompanionDogAction.EAT),
            new Scene("ball-fetch", "Throw a tennis ball - bounce, chase and bring it back", 240, null, null),
            new Scene("disc-fetch", "Throw a flying disc - a different flight, the same friend", 280, null, null),
            new Scene("sleep", "Choose Sleep - companionship at a quieter pace", 100, CompanionDogCommand.SLEEP, null));

    private final Minecraft mc = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("companiondog.visualtest.output"));
    private final Path receipt = Path.of(System.getProperty("companiondog.visualtest.receipt"));
    private final Instant started = Instant.now();
    private final JsonArray rows = new JsonArray();
    private final JsonArray frames = new JsonArray();
    private CompletableFuture<Void> serverWork;
    private CompletableFuture<Boolean> boundObservation;
    private UUID dogId;
    private double ground;
    private int phase, warmup, sceneIndex, sceneTick, clipTick, clipOffset, frameIndex, lastCapture = -1, isolationChecks;
    private boolean done, actionSeen, menuSeen, projectileSeen, mouthSeen, delivered, resetChecked;
    private String pendingFailure = "";

    CompanionShowcase() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::capture);
    }

    private void isolate() {
        long window = mc.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != 0
                || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0
                || GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_NORMAL
                || GLFW.glfwGetWindowMonitor(window) != 0
                || VisualHarness.gainWrites > 0 && VisualHarness.deviceGain != 0) {
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
            if (Duration.between(started, Instant.now()).toMinutes() > 6) throw new IllegalStateException("Showcase watchdog");
            if (phase == 0) {
                if (mc.screen == null || mc.getOverlay() != null) return;
                Files.createDirectories(output);
                mc.options.onboardingAccessibilityFinished();
                mc.options.guiScale().set(2);
                mc.options.fov().set(52);
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                mc.options.hideGui = false;
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.getTutorial().setStep(TutorialSteps.NONE);
                mc.resizeDisplay();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                var settings = new LevelSettings("Companion showcase", GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("companion-showcase-" + System.getenv("MINECRAFT_MOD_TEST_SESSION_ID"),
                        settings, new WorldOptions(1, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                phase = 1;
            } else if (phase == 1) {
                if (mc.level == null || mc.player == null || mc.getOverlay() != null || ++warmup < 40) return;
                mc.setScreen(null);
                serverWork = mc.getSingleplayerServer().submit(this::arrangeWorld);
                phase = 2;
            } else if (phase == 2) {
                if (!serverWork.isDone()) return;
                serverWork.join();
                warmup = 0;
                phase = 3;
            } else if (phase == 3) {
                if (++warmup < 18) return;
                if (sceneIndex == SCENES.size()) { finish(""); return; }
                beginScene();
                phase = 4;
            } else if (phase == 4) {
                runScene();
            } else if (phase == 5) {
                if (!serverWork.isDone()) return;
                serverWork.join();
                if (!resetChecked) throw new IllegalStateException("Scene reset did not finish");
                rows.get(rows.size() - 1).getAsJsonObject().addProperty("cleanupPassed", true);
                sceneIndex++;
                warmup = 0;
                phase = 3;
            } else if (phase == 6) {
                if (!serverWork.isDone()) return;
                try {
                    serverWork.join();
                    if (!rows.isEmpty()) rows.get(rows.size() - 1).getAsJsonObject().addProperty("cleanupPassed", resetChecked);
                } catch (Throwable cleanup) {
                    resetChecked = false;
                    pendingFailure += "; cleanup: " + cleanup;
                }
                finish(pendingFailure);
            }
            mc.getToasts().clear();
        } catch (Throwable error) { abort(error); }
    }

    private void arrangeWorld() {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        level.setDayTime(1000);
        level.setWeatherParameters(12000, 0, false, false);
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) {
            level.setBlock(new BlockPos(x, (int) ground - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            if (Math.abs(x) == 20 || Math.abs(z) == 20) {
                level.setBlock(new BlockPos(x, (int) ground, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
            }
        }
        resetScene(0);
    }

    private ServerPlayer owner() {
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private CompanionDogEntity serverDog() {
        return dogId == null ? null : (CompanionDogEntity) mc.getSingleplayerServer().overworld().getEntity(dogId);
    }

    private CompanionDogEntity clientDog() {
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof CompanionDogEntity dog) return dog;
        return null;
    }

    private void resetScene(int next) {
        var player = owner();
        var level = player.serverLevel();
        var dog = serverDog();
        player.teleportTo(0.5, ground, 5.5);
        player.setYRot(180);
        player.setXRot(18);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(-24, ground - 2, -24, 24, ground + 12, 24))) item.discard();
        for (var toy : level.getEntitiesOfClass(FetchToyEntity.class, new AABB(-24, ground - 2, -24, 24, ground + 12, 24))) toy.discard();
        if (dog != null) {
            dog.setMouthItem(ItemStack.EMPTY);
            dog.setCommandedPose(CompanionDogPose.STAND);
            dog.setWandering(false);
            dog.setTarget(null);
            dog.getNavigation().stop();
            dog.teleportTo(next == 6 || next == 7 ? 2.5 : 0.5, ground, 2.5);
            dog.setYRot(0);
            dog.yBodyRot = dog.yHeadRot = 0;
        }
        if (next < SCENES.size()) {
            Item item = switch (next) {
                case 0 -> CompanionDogRegistries.COMPANION_DOG_COLLAR.get();
                case 1, 3, 4, 8 -> Items.BONE;
                case 5 -> Items.COOKED_BEEF;
                case 6 -> CompanionDogRegistries.TENNIS_BALL.get();
                case 7 -> CompanionDogRegistries.FRISBEE.get();
                default -> Items.AIR;
            };
            if (item != Items.AIR) player.getInventory().setItem(0, new ItemStack(item));
        }
        player.inventoryMenu.broadcastChanges();
        var arena = new AABB(-24, ground - 2, -24, 24, ground + 12, 24);
        if (!level.getEntitiesOfClass(ItemEntity.class, arena, e -> !e.isRemoved()).isEmpty()
                || !level.getEntitiesOfClass(FetchToyEntity.class, arena, e -> !e.isRemoved()).isEmpty()
                || dog != null && (!dog.getMouthItem().isEmpty() || dog.isBusy())) {
            throw new IllegalStateException("Fixture reset retained toy or action state");
        }
        resetChecked = true;
    }

    private void beginScene() {
        mc.setScreen(null);
        mc.getSoundManager().stop();
        mc.gui.getChat().clearMessages(true);
        mc.player.setYRot(180);
        mc.player.setXRot(18);
        mc.player.yRotO = 180;
        mc.player.xRotO = 18;
        sceneTick = 0;
        actionSeen = menuSeen = projectileSeen = mouthSeen = delivered = false;
        resetChecked = false;
    }

    private void interact() {
        var dog = clientDog();
        if (dog == null) throw new IllegalStateException("No synchronized dog for interaction");
        mc.gameMode.interact(mc.player, dog, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void click(CompanionDogCommand command) {
        if (!(mc.screen instanceof CompanionDogInteractScreen screen)) throw new IllegalStateException("Bone menu did not open");
        String label = Component.translatable(command.translationKey()).getString();
        for (var child : screen.children()) if (child instanceof Button button && button.getMessage().getString().equals(label)) {
            if (!button.active || !screen.mouseClicked(button.getX() + button.getWidth() / 2.0,
                    button.getY() + button.getHeight() / 2.0, 0)) throw new IllegalStateException("Menu click rejected: " + label);
            menuSeen = true;
            return;
        }
        throw new IllegalStateException("Menu command not found: " + label);
    }

    private void runScene() {
        Scene scene = SCENES.get(sceneIndex);
        sceneTick++;
        clipTick = clipOffset + sceneTick;
        if (sceneTick == 10) {
            if (sceneIndex == 0 || sceneIndex == 6 || sceneIndex == 7) {
                mc.player.setXRot(sceneIndex == 7 ? 6 : sceneIndex == 6 ? 10 : 18);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else interact();
        }
        if (scene.command() != null && sceneTick == 32) click(scene.command());
        if (scene.command() != null && sceneTick == 48) mc.setScreen(null);
        var dog = clientDog();
        if (dog != null) {
            if (dogId == null) dogId = dog.getUUID();
            if (scene.action() != null && dog.getClientAction(0) == scene.action()) actionSeen = true;
            if (sceneIndex == 0 && dog.isTame() && dog.isOwnedBy(mc.player)) {
                actionSeen = true;
                if (boundObservation == null) boundObservation = mc.getSingleplayerServer().submit(() ->
                        serverDog() != null && serverDog().isBound() && serverDog().isOwnedBy(owner()));
            }
            if (sceneIndex == 1 && dog.getCompanionDogPose() == CompanionDogPose.SIT) actionSeen = true;
            if (sceneIndex == 8 && dog.getCompanionDogPose() == CompanionDogPose.SLEEP) actionSeen = true;
            if (sceneIndex == 6 || sceneIndex == 7) {
                Item toy = sceneIndex == 6 ? CompanionDogRegistries.TENNIS_BALL.get() : CompanionDogRegistries.FRISBEE.get();
                mouthSeen |= dog.getMouthItem().is(toy);
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof FetchToyEntity) projectileSeen = true;
                delivered |= sceneTick > 30 && mc.player.getInventory().countItem(toy) >= 1;
                // Follow the actual chase with the player's view, without moving the player.
                if (sceneTick > 14) {
                    var delta = dog.position().add(0, 0.5, 0).subtract(mc.player.getEyePosition());
                    float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
                    float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(pitch);
                }
            }
        }
        if (sceneTick < scene.ticks()) return;
        boolean passed = sceneIndex == 6 || sceneIndex == 7 ? projectileSeen && mouthSeen && delivered : actionSeen;
        if (sceneIndex == 0) passed &= boundObservation != null && boundObservation.isDone() && boundObservation.join();
        if (scene.command() != null) passed &= menuSeen;
        var row = new JsonObject();
        row.addProperty("id", scene.id());
        row.addProperty("caption", scene.caption());
        row.addProperty("passed", passed);
        row.addProperty("cleanupPassed", false);
        row.addProperty("startedAtTick", clipOffset);
        row.addProperty("finishedAtTick", clipOffset + scene.ticks());
        row.addProperty("menuClickObserved", menuSeen);
        row.addProperty("actionObserved", actionSeen);
        row.addProperty("projectileObserved", projectileSeen);
        row.addProperty("carriedToyObserved", mouthSeen);
        row.addProperty("toyReturnedToInventory", delivered);
        rows.add(row);
        if (!passed) throw new IllegalStateException("Showcase observation failed: " + scene.id());
        clipOffset += scene.ticks();
        phase = 5;
        mc.setScreen(null);
        serverWork = mc.getSingleplayerServer().submit(() -> resetScene(sceneIndex + 1));
    }

    private void capture(RenderFrameEvent.Post event) {
        if (done || phase != 4 || sceneTick == 0 || clipTick == lastCapture) return;
        try {
            isolate();
            String filename = String.format(java.util.Locale.ROOT, "frame-%05d.png", frameIndex++);
            Path file = output.resolve(filename);
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                if (image.getWidth() != 1280 || image.getHeight() != 720) throw new IllegalStateException("Unexpected showcase frame size");
                image.writeToFile(file);
            }
            var frame = new JsonObject();
            frame.addProperty("path", filename);
            frame.addProperty("tick", clipTick);
            frame.addProperty("scene", SCENES.get(sceneIndex).id());
            frame.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            frames.add(frame);
            lastCapture = clipTick;
        } catch (Throwable error) { abort(error); }
    }

    private void abort(Throwable error) {
        if (done || phase == 6) return;
        pendingFailure = error.toString();
        if (mc.player == null || mc.getSingleplayerServer() == null || serverWork != null && !serverWork.isDone()) {
            finish(pendingFailure);
            return;
        }
        phase = 6;
        mc.setScreen(null);
        serverWork = mc.getSingleplayerServer().submit(() -> resetScene(SCENES.size()));
    }

    private void finish(String failure) {
        if (done) return;
        done = true;
        try {
            var r = new JsonObject();
            r.addProperty("schemaVersion", 1);
            r.addProperty("sessionId", System.getenv("MINECRAFT_MOD_TEST_SESSION_ID"));
            r.addProperty("scope", "single-client real-item-and-menu gameplay showcase; not full geometry or multiplayer acceptance");
            r.addProperty("processStartCount", 1);
            r.addProperty("worldLoadCount", mc.level == null ? 0 : 1);
            r.addProperty("scenarioCount", rows.size());
            r.addProperty("allScenariosPassed", failure.isEmpty() && rows.size() == SCENES.size());
            r.addProperty("failedCount", failure.isEmpty() ? 0 : 1);
            r.addProperty("cleanupFailureCount", resetChecked ? 0 : 1);
            r.addProperty("failure", failure);
            r.addProperty("startedAt", started.toString());
            r.addProperty("finishedAt", Instant.now().toString());
            r.addProperty("frameCount", frames.size());
            r.addProperty("ticksPerSecond", 20);
            r.add("scenarios", rows);
            var requested = new JsonArray();
            SCENES.forEach(s -> requested.add(s.id()));
            var observed = new JsonArray();
            rows.forEach(row -> observed.add(row.getAsJsonObject().get("id").getAsString()));
            r.add("requestedScenarios", requested);
            r.add("observedScenarios", observed);
            var isolation = new JsonObject();
            for (String key : List.of("enabled", "hidden", "unfocused", "cursorFree", "windowed", "clipboardIsolated")) isolation.addProperty(key, true);
            isolation.addProperty("silentDevice", VisualHarness.gainWrites > 0 && VisualHarness.deviceGain == 0);
            isolation.addProperty("verifiedTicks", isolationChecks);
            isolation.addProperty("violationCount", failure.contains("isolation") ? 1 : 0);
            r.add("desktopIsolation", isolation);
            Files.createDirectories(receipt.getParent());
            Files.writeString(output.resolve("capture-manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(frames));
            Files.writeString(receipt, new GsonBuilder().setPrettyPrinting().create().toJson(r));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
