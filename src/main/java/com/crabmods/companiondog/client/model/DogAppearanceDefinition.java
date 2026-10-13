package com.crabmods.companiondog.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rig geometry, textures and mouth sockets, from the bundled default appearance or a resource pack. */
public record DogAppearanceDefinition(int textureWidth, int textureHeight, float scale,
                                      float motionScale, Map<String, Bone> bones,
                                      Map<String, String> textures, Map<String, Socket> sockets) {
    public static final Map<String, String> PARENTS;
    static {
        Map<String, String> parents = new LinkedHashMap<>();
        parents.put("root", "");
        parents.put("body", "root");
        parents.put("head", "body");
        parents.put("jaw", "head");
        parents.put("tongue", "jaw");
        parents.put("tail", "body");
        parents.put("tail_tip", "tail");
        for (String side : List.of("right", "left")) {
            parents.put(side + "_ear", "head");
            parents.put(side + "_ear_tip", side + "_ear");
            for (String end : List.of("front", "hind")) {
                parents.put(side + "_" + end + "_leg", "body");
                parents.put(side + "_" + end + "_paw", side + "_" + end + "_leg");
            }
        }
        PARENTS = java.util.Collections.unmodifiableMap(parents);
    }
    public record Vector(float x, float y, float z) {}
    public record Cube(Vector origin, Vector size, int u, int v, boolean mirror) {}
    public record Bone(Vector pivot, Vector rotation, List<Cube> cubes) {}
    public record Socket(Vector offset, Vector rotation, float scale) {}
    private static final Vector ZERO = new Vector(0, 0, 0);
    private static final Socket EMPTY_SOCKET = new Socket(ZERO, ZERO, 1);

    public static DogAppearanceDefinition empty() {
        Map<String, Bone> bones = new LinkedHashMap<>();
        PARENTS.keySet().forEach(name -> bones.put(name, new Bone(ZERO, ZERO, List.of())));
        return new DogAppearanceDefinition(64, 64, 1, 1, Map.copyOf(bones), Map.of(), Map.of());
    }
    public boolean hasGeometry() { return bones.values().stream().anyMatch(bone -> !bone.cubes().isEmpty()); }
    public Socket socket(String name) { return sockets.getOrDefault(name, EMPTY_SOCKET); }

    /** Textures may be a uniform integer multiple (1-8x) of the UV grid for finer pixel art. */
    public void validateTextureDimensions(int width, int height) {
        if (width < textureWidth || height < textureHeight || width > 4096 || height > 4096
                || width % textureWidth != 0 || height % textureHeight != 0
                || width / textureWidth != height / textureHeight || width / textureWidth > 8) {
            throw invalid("PNG resolution must be a uniform integer multiple (1 through 8) of texture_size");
        }
    }

    public static DogAppearanceDefinition parse(String json) {
        if (json.length() > 262144) throw invalid("definition exceeds 256 KiB");
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        keys(object, Set.of("schema_version", "texture_size", "scale", "motion_scale", "bones", "textures", "sockets"));
        integer(object.get("schema_version"), 1, 1);
        var dimensions = object.getAsJsonArray("texture_size");
        if (dimensions == null || dimensions.size() != 2) throw invalid("texture_size must contain two integers");
        int width = integer(dimensions.get(0), 1, 4096), height = integer(dimensions.get(1), 1, 4096);
        float scale = number(object.get("scale"), 0.05F, 8), motion = number(object.get("motion_scale"), 0.05F, 8);
        JsonObject inputBones = object.getAsJsonObject("bones");
        if (inputBones == null || !inputBones.keySet().equals(PARENTS.keySet())) {
            throw invalid("bones must match the documented rig, including empty optional bones");
        }
        Map<String, Bone> bones = new LinkedHashMap<>();
        int cubeCount = 0;
        for (String name : PARENTS.keySet()) {
            JsonObject bone = inputBones.getAsJsonObject(name);
            keys(bone, Set.of("pivot", "rotation", "cubes"));
            Vector pivot = vector(bone.get("pivot"), -256, 256), rotation = vector(bone.get("rotation"), -360, 360);
            var inputCubes = bone.getAsJsonArray("cubes");
            if (inputCubes == null) throw invalid("missing cubes for " + name);
            cubeCount += inputCubes.size();
            if (cubeCount > 512) throw invalid("maximum 512 cubes per model");
            var cubes = new java.util.ArrayList<Cube>();
            for (JsonElement value : inputCubes) {
                JsonObject cube = value.getAsJsonObject();
                keys(cube, Set.of("origin", "size", "uv", "mirror"));
                Vector origin = vector(cube.get("origin"), -256, 256), size = vector(cube.get("size"), 0.01F, 256);
                var uv = cube.getAsJsonArray("uv");
                if (uv == null || uv.size() != 2) throw invalid("uv must contain two integers");
                int u = integer(uv.get(0), 0, width - 1), v = integer(uv.get(1), 0, height - 1);
                if (u + 2 * (size.x() + size.z()) > width || v + size.y() + size.z() > height) throw invalid("box UV exceeds texture bounds");
                cubes.add(new Cube(origin, size, u, v, cube.has("mirror") && cube.get("mirror").getAsBoolean()));
            }
            bones.put(name, new Bone(pivot, rotation, List.copyOf(cubes)));
        }
        Map<String, String> textures = new LinkedHashMap<>();
        JsonObject inputTextures = object.getAsJsonObject("textures");
        if (inputTextures != null) {
            keys(inputTextures, Set.of("normal", "blink", "happy", "sleep", "collar"));
            for (String key : inputTextures.keySet()) {
                String location = inputTextures.get(key).getAsString();
                if (!location.isEmpty() && !location.matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png")) throw invalid("invalid texture location");
                if (location.contains("..")) throw invalid("texture path traversal");
                if (!location.isEmpty()) textures.put(key, location);
            }
        }
        if (cubeCount > 0 && !textures.containsKey("normal")) throw invalid("geometry requires a normal texture");
        Map<String, Socket> sockets = new LinkedHashMap<>();
        JsonObject inputSockets = object.getAsJsonObject("sockets");
        if (inputSockets != null) {
            keys(inputSockets, Set.of("default", "tennis_ball", "frisbee"));
            for (String key : inputSockets.keySet()) {
                JsonObject socket = inputSockets.getAsJsonObject(key);
                keys(socket, Set.of("offset", "rotation", "scale"));
                sockets.put(key, new Socket(vector(socket.get("offset"), -256, 256), vector(socket.get("rotation"), -360, 360), number(socket.get("scale"), 0.01F, 8)));
            }
        }
        if (cubeCount > 0 && !sockets.keySet().containsAll(Set.of("default", "tennis_ball", "frisbee"))) throw invalid("geometry requires all three mouth sockets");
        return new DogAppearanceDefinition(width, height, scale, motion, Map.copyOf(bones), Map.copyOf(textures), Map.copyOf(sockets));
    }
    private static void keys(JsonObject object, Set<String> allowed) {
        if (object == null || !allowed.containsAll(object.keySet())) throw invalid("unknown or missing object fields");
    }
    private static Vector vector(JsonElement value, float min, float max) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) throw invalid("expected a three-number vector");
        var values = value.getAsJsonArray();
        return new Vector(number(values.get(0), min, max), number(values.get(1), min, max), number(values.get(2), min, max));
    }
    private static int integer(JsonElement value, int min, int max) {
        float result = number(value, min, max);
        if (result != Math.rint(result)) throw invalid("expected integer");
        return (int) result;
    }
    private static float number(JsonElement value, float min, float max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("expected number");
        float result = value.getAsFloat();
        if (!Float.isFinite(result) || result < min || result > max) throw invalid("number outside bounds");
        return result;
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Dog appearance: " + message); }
}
