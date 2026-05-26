package com.kuronami.claimintrusionalert.death;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/**
 * Builds a {@link IntrusionRecord} from the vanilla death the instant it
 * happens. Pure extraction of certain facts — the responsible entity,
 * distance/direction, dimension, day, nearby hostile count — plus
 * vanilla's own death Component for the cause text. No heuristic
 * attribution, so it cannot be confidently wrong.
 */
public final class IntrusionAnalyzer {

    private static final String[] WIND =
        {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /** Hostiles within this radius of the death are counted as context. */
    private static final double NEARBY_RADIUS = 16.0;

    private IntrusionAnalyzer() {}

    public static IntrusionRecord analyze(ServerPlayer player, DamageSource source) {
        ServerLevel level = (ServerLevel) player.level();
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();

        String dim = shortDim(level);
        long day = level.getDayTime() / 24000L;

        // Vanilla already localizes the cause; capture it and replay it
        // later so it re-localizes per reader.
        var deathMessage = player.getCombatTracker().getDeathMessage();

        String killerType = "";
        int killerDistance = -1;
        String killerDir = "";
        Entity killer = source.getEntity();
        if (killer != null && killer != player) {
            killerType = pretty(BuiltInRegistries.ENTITY_TYPE
                .getKey(killer.getType()).toString());
            killerDistance = (int) Math.round(killer.distanceTo(player));
            killerDir = compass8(player, killer);
        }

        int nearbyHostiles = level.getEntitiesOfClass(
            Monster.class,
            new AABB(player.blockPosition()).inflate(NEARBY_RADIUS)).size();

        return new IntrusionRecord(deathMessage, x, y, z, dim, day,
            killerType, killerDistance, killerDir, nearbyHostiles);
    }

    /** 8-wind compass bearing from {@code from} toward {@code to}. */
    private static String compass8(Entity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // MC: -Z is north, +X is east. atan2(dx, -dz): 0=N, 90=E.
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        if (bearing < 0) bearing += 360.0;
        int idx = (int) Math.round(bearing / 45.0) & 7;
        return WIND[idx];
    }

    private static String shortDim(ServerLevel level) {
        var rl = level.dimension().location();
        return "minecraft".equals(rl.getNamespace()) ? rl.getPath() : rl.toString();
    }

    private static String pretty(String typeId) {
        return typeId.startsWith("minecraft:")
            ? typeId.substring("minecraft:".length()) : typeId;
    }
}
