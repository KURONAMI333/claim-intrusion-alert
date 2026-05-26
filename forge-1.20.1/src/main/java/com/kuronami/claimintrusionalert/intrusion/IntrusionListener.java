package com.kuronami.claimintrusionalert.death;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * On death: build the forensic record, store it (so {@code /howdididie}
 * can re-show it or an op can look it up), and <em>immediately post the
 * full styled report to the player's chat</em>. No command needed to
 * learn what just killed you — the report finds you.
 *
 * <p>v0.1 keeps only the last death per player, in memory, cleared on
 * server stop (the persistent multi-death journal is a separate mod).
 * Read-only against the world.
 */
public class IntrusionListener {

    private static final Map<UUID, IntrusionRecord> LAST_DEATHS =
        new ConcurrentHashMap<>();

    public static IntrusionRecord lastDeath(UUID uuid) {
        return LAST_DEATHS.get(uuid);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IntrusionRecord record = IntrusionAnalyzer.analyze(player, event.getSource());
        LAST_DEATHS.put(player.getUUID(), record);

        // Auto-post the full styled report. It lands in chat and is
        // there when the death screen closes / they respawn.
        for (Component line : IntrusionReport.render(record)) {
            player.sendSystemMessage(line);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LAST_DEATHS.clear();
    }
}
