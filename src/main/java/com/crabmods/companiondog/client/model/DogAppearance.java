package com.crabmods.companiondog.client.model;

import com.crabmods.companiondog.CompanionDogMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Rebuilt with entity renderers on resource reload. The mod bundles the generated default
 * appearance; a resource pack can replace it. Invalid input leaves an empty rig.
 */
public record DogAppearance(DogAppearanceDefinition definition) {
    public static final ResourceLocation LOCATION = CompanionDogMod.id("appearance/dog.json");

    public static DogAppearance load(ResourceManager resources) {
        var resource = resources.getResource(LOCATION);
        if (resource.isEmpty()) return new DogAppearance(DogAppearanceDefinition.empty());
        try (var stream = resource.get().open()) {
            byte[] bytes = stream.readNBytes(262145);
            if (bytes.length > 262144) throw new IllegalArgumentException("Dog appearance exceeds 256 KiB");
            var definition = DogAppearanceDefinition.parse(new String(bytes, StandardCharsets.UTF_8));
            for (String path : definition.textures().values()) {
                var texture = resources.getResource(ResourceLocation.parse(path))
                        .orElseThrow(() -> new IllegalArgumentException("Missing appearance texture: " + path));
                try (var input = texture.open()) {
                    byte[] png = input.readNBytes(8388609);
                    if (png.length > 8388608) throw new IllegalArgumentException("Texture exceeds 8 MiB: " + path);
                    validatePngHeader(png, definition);
                    try (var decoded = NativeImage.read(png)) {
                        definition.validateTextureDimensions(decoded.getWidth(), decoded.getHeight());
                    }
                }
            }
            return new DogAppearance(definition);
        } catch (Exception failure) {
            CompanionDogMod.LOGGER.error("External dog appearance rejected; rendering remains empty", failure);
            return new DogAppearance(DogAppearanceDefinition.empty());
        }
    }

    private static void validatePngHeader(byte[] png, DogAppearanceDefinition definition) {
        if (png.length < 33) throw new IllegalArgumentException("Truncated appearance PNG");
        var header = java.nio.ByteBuffer.wrap(png);
        if (header.getLong() != 0x89504e470d0a1a0aL || header.getInt() != 13 || header.getInt() != 0x49484452) {
            throw new IllegalArgumentException("Invalid PNG header");
        }
        definition.validateTextureDimensions(header.getInt(), header.getInt());
    }
    public ResourceLocation texture(String name) {
        String path = definition.textures().getOrDefault(name, definition.textures().get("normal"));
        return path == null ? null : ResourceLocation.parse(path);
    }
    public ModelPart bake() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("", mesh.getRoot());
        DogAppearanceDefinition.PARENTS.forEach((name, parent) -> {
            var bone = definition.bones().get(name);
            var builder = CubeListBuilder.create();
            for (var cube : bone.cubes()) {
                builder.texOffs(cube.u(), cube.v()).mirror(cube.mirror()).addBox(cube.origin().x(), cube.origin().y(), cube.origin().z(), cube.size().x(), cube.size().y(), cube.size().z());
            }
            var pivot = bone.pivot();
            var rotation = bone.rotation();
            parts.put(name, parts.get(parent).addOrReplaceChild(name, builder, PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), rotation.x() * Mth.DEG_TO_RAD, rotation.y() * Mth.DEG_TO_RAD, rotation.z() * Mth.DEG_TO_RAD)));
        });
        return LayerDefinition.create(mesh, definition.textureWidth(), definition.textureHeight()).bakeRoot();
    }
}
