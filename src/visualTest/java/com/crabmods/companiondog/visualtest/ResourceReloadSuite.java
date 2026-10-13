package com.crabmods.companiondog.visualtest;

import com.crabmods.companiondog.client.CompanionDogRenderer;
import com.crabmods.companiondog.client.model.DogAppearance;
import com.crabmods.companiondog.client.model.DogAppearanceDefinition;
import com.crabmods.companiondog.entity.CompanionDogAction;
import com.crabmods.companiondog.entity.CompanionDogEntity;
import com.crabmods.companiondog.entity.CompanionDogPose;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises the real pack repository, resource reload and rebuilt entity renderer in one world. */
final class ResourceReloadSuite {
    static final List<String> IDS = List.of("reload.directory", "reload.zip", "reload.priority",
            "reload.blank", "reload.invalid_json", "reload.missing_texture", "reload.invalid_png",
            "reload.invalid_resolution", "reload.restore_default");
    private final Minecraft mc;
    private final Path output;
    private final String prefix = "companiondog-reload-" + System.getenv("MINECRAFT_MOD_TEST_SESSION_ID") + "-";
    private final List<String> baseline;
    private final Map<String, String> packs = new LinkedHashMap<>();
    private final JsonArray inputs = new JsonArray();
    private final JsonArray observations = new JsonArray();
    private final DogAppearanceDefinition original;
    private final JsonObject originalJson;
    private CompletableFuture<Void> reload;
    private CompanionDogRenderer previousRenderer;
    private String current = "";
    private boolean ready;
    private int startedTick;

    ResourceReloadSuite(Minecraft mc, Path output) throws Exception {
        this.mc = mc;
        this.output = output;
        baseline = List.copyOf(mc.getResourcePackRepository().getSelectedIds());
        original = DogAppearance.load(mc.getResourceManager()).definition();
        try (var stream = mc.getResourceManager().getResource(DogAppearance.LOCATION).orElseThrow().open()) {
            originalJson = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        byte[] normal;
        try (var stream = mc.getResourceManager().getResource(ResourceLocation.parse(original.textures().get("normal"))).orElseThrow().open()) {
            normal = stream.readAllBytes();
        }
        create("directory", override("directory", 1), normal, false);
        create("zip", override("zip", -1), normal, true);
        var blank = originalJson.deepCopy();
        blank.getAsJsonObject("bones").entrySet().forEach(e -> e.getValue().getAsJsonObject().add("cubes", new JsonArray()));
        blank.add("textures", new JsonObject());
        blank.add("sockets", new JsonObject());
        create("blank", blank.toString(), null, false);
        create("invalid_json", "{", null, false);
        create("missing_texture", override("missing_texture", 1), null, false);
        create("invalid_png", override("invalid_png", 1), "not a PNG".getBytes(StandardCharsets.UTF_8), false);
        var resolution = JsonParser.parseString(override("invalid_resolution", 1)).getAsJsonObject();
        var dimensions = new JsonArray();
        dimensions.add(original.textureWidth() + 1);
        dimensions.add(original.textureHeight());
        resolution.add("texture_size", dimensions);
        var header = java.nio.ByteBuffer.wrap(normal);
        var invalidDimensions = DogAppearanceDefinition.parse(resolution.toString());
        boolean rejected = false;
        try {
            invalidDimensions.validateTextureDimensions(header.getInt(16), header.getInt(20));
        } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new IllegalStateException("Resolution fixture is not invalid");
        create("invalid_resolution", resolution.toString(), normal, false);
        Files.writeString(output.resolve("reload-inputs.json"), inputs.toString());
    }

    private String override(String name, int offset) {
        var json = originalJson.deepCopy();
        // Different namespace/path and a small diagnostic joint offset prove both carriers were replaced.
        var pivot = json.getAsJsonObject("bones").getAsJsonObject("body").getAsJsonArray("pivot");
        pivot.set(0, new com.google.gson.JsonPrimitive(pivot.get(0).getAsFloat() + offset));
        json.getAsJsonObject("textures").addProperty("normal", "reload_fixture:textures/remapped/" + name + ".png");
        return json.toString();
    }

    private void create(String name, String json, byte[] png, boolean zip) throws Exception {
        Path path = mc.gameDirectory.toPath().resolve("resourcepacks").resolve(prefix + name + (zip ? ".zip" : ""));
        if (Files.exists(path)) throw new IllegalStateException("Reload fixture already exists: " + path);
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":\"Isolated reload fixture\"}}".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/companiondog/appearance/dog.json", json.getBytes(StandardCharsets.UTF_8));
        if (png != null) entries.put("assets/reload_fixture/textures/remapped/" + name + ".png", png);
        if (zip) {
            try (var stream = new ZipOutputStream(Files.newOutputStream(path))) {
                for (var entry : entries.entrySet()) {
                    stream.putNextEntry(new ZipEntry(entry.getKey()));
                    stream.write(entry.getValue());
                    stream.closeEntry();
                }
            }
        } else {
            for (var entry : entries.entrySet()) {
                Path file = path.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
        }
        packs.put(name, "file/" + path.getFileName());
        var row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("path", path.toString());
        row.addProperty("definitionSha256", hash(json.getBytes(StandardCharsets.UTF_8)));
        if (png != null) row.addProperty("textureSha256", hash(png));
        inputs.add(row);
    }

    boolean tick(String id, CompanionDogEntity dog) throws Exception {
        if (!id.equals(current)) {
            current = id;
            ready = false;
            startedTick = mc.player.tickCount;
            previousRenderer = (CompanionDogRenderer) mc.getEntityRenderDispatcher().getRenderer(dog);
            var repository = mc.getResourcePackRepository();
            repository.reload();
            var selected = new ArrayList<>(baseline);
            String name = id.substring("reload.".length());
            if (name.equals("priority")) {
                selected.add(packs.get("directory"));
                selected.add(packs.get("zip"));
            } else if (!name.equals("restore_default")) selected.add(packs.get(name));
            repository.setSelected(selected);
            if (!repository.getSelectedIds().containsAll(selected)) throw new IllegalStateException("Pack was not selected: " + selected);
            reload = mc.reloadResourcePacks();
            return false;
        }
        if (ready) return true;
        if (!reload.isDone() || mc.getOverlay() != null) return false;
        reload.join();
        var appearance = DogAppearance.load(mc.getResourceManager());
        var renderer = (CompanionDogRenderer) mc.getEntityRenderDispatcher().getRenderer(dog);
        if (renderer == previousRenderer) throw new IllegalStateException("Entity renderer was not rebuilt: " + id);
        boolean visible = expectsGeometry(id);
        if (appearance.definition().hasGeometry() != visible) throw new IllegalStateException("Wrong resource fallback: " + id);
        int[] count = {0};
        renderer.getModel().root().visit(new com.mojang.blaze3d.vertex.PoseStack(),
                (matrix, path, number, cube) -> count[0]++);
        int cubes = count[0];
        if ((cubes > 0) != visible) throw new IllegalStateException("Stale baked geometry: " + id);
        dog.setDebugAnimation(CompanionDogPose.STAND, CompanionDogAction.NONE, 0);
        String expectedTexture = id.equals("reload.directory") ? "reload_fixture:textures/remapped/directory.png"
                : id.equals("reload.zip") || id.equals("reload.priority") ? "reload_fixture:textures/remapped/zip.png"
                : id.equals("reload.restore_default") ? original.textures().get("normal") : null;
        var texture = renderer.getTextureLocation(dog);
        if (!java.util.Objects.equals(texture == null ? null : texture.toString(), expectedTexture)) {
            throw new IllegalStateException("Stale renderer texture: " + id + " " + texture);
        }
        if (visible) {
            float expected = original.bones().get("body").pivot().x()
                    + (id.equals("reload.directory") ? 1 : id.equals("reload.restore_default") ? 0 : -1);
            if (Math.abs(renderer.getModel().root().getChild("body").getInitialPose().x - expected) > 0.0001F) {
                throw new IllegalStateException("Stale baked joint: " + id);
            }
        }
        var row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("rendererRebuilt", true);
        row.addProperty("geometryVisible", visible);
        row.addProperty("bakedCubeCount", cubes);
        row.addProperty("texture", expectedTexture);
        row.addProperty("startedAtTick", startedTick);
        row.addProperty("finishedAtTick", mc.player.tickCount);
        var selected = new JsonArray();
        mc.getResourcePackRepository().getSelectedIds().forEach(selected::add);
        row.add("selectedPacks", selected);
        observations.add(row);
        Files.writeString(output.resolve("reload-observations.json"), observations.toString());
        ready = true;
        return true;
    }

    static boolean expectsGeometry(String id) {
        return id.equals("reload.directory") || id.equals("reload.zip")
                || id.equals("reload.priority") || id.equals("reload.restore_default");
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
