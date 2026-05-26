package com.kuronami.claimintrusionalert.death;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * On death: build the forensic record, store it (so {@code /howdididie}
 * can re-show it or an op can look it up), and <em>immediately post the
 * full styled report to the player's chat</em>. No command needed to
 * learn what just killed you — the report finds you.
 *
 * <p>v0.1 keeps only the last death per player, in memory, cleared on
 * server stop (the persistent multi-death journal is a separate mod).
 * Read-only against the world.
 *
 * <p>Fabric variant: static helpers driven by the {@code AFTER_DEATH}
 * and {@code SERVER_STOPPING} hooks wired in {@code ClaimIntrusionAlertFabric}.
 */
public final class IntrusionListener {

    private static final Map<UUID, IntrusionRecord> LAST_DEATHS =
        new ConcurrentHashMap<>();

    private IntrusionListener() {
    }

    public static IntrusionRecord lastDeath(UUID uuid) {
        return LAST_DEATHS.get(uuid);
    }

    public static void clear() {
        LAST_DEATHS.clear();
    }

    public static void record(ServerPlayer player, DamageSource source) {
        IntrusionRecord r = IntrusionAnalyzer.analyze(player, source);
        LAST_DEATHS.put(player.getUUID(), r);

        // Auto-post the full styled report. It lands in chat and is
        // there when the death screen closes / they respawn.
        for (Component line : IntrusionReport.render(r)) {
            player.sendSystemMessage(line);
        }
    }
}
