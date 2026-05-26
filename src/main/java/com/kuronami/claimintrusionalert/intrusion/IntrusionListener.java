package com.kuronami.claimintrusionalert.intrusion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * FTB Chunks 妨害イベントを vanilla event 経由で観測し、claim owner に
 * chat 通知する listener。
 *
 * <p>方針: vanilla {@code BlockEvent.BreakEvent} / {@code PlayerInteractEvent.RightClickBlock}
 * を {@link EventPriority#HIGHEST} で hook し、{@code event.isCanceled() == true} なら
 * 「誰かが妨害した」と判断。FTB Chunks {@code FTBChunksAPI.api().getManager().getChunk(...)}
 * で claim を解決、claim ありなら owner + team に通知。
 *
 * <p>cooldown: {@code (intruderUUID, ChunkDimPos)} ペアで {@link #COOLDOWN_MS} ms 抑止。
 *
 * <p>⚠ TODO: 実装本体 (FTB Chunks API 呼出) は次セッション。現状はイベント受信して
 * {@link IntrusionAnalyzer#analyze} に投げるだけのスケルトン。
 */
public class IntrusionListener {

    /** 同一 (侵入者 × claim) ペアの通知抑止時間。 */
    public static final long COOLDOWN_MS = 5 * 60 * 1000L;

    /** key = "uuid@dim:chunkX,chunkZ"、value = 最終通知時刻 (ms)。 */
    private static final Map<String, Long> LAST_ALERT = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level() instanceof ServerLevel level) {
            handleIntrusion(player, level, event.getPos(), IntrusionRecord.Action.BREAK);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() instanceof ServerLevel level) {
            handleIntrusion(player, level, event.getPos(), IntrusionRecord.Action.INTERACT);
        }
    }

    private void handleIntrusion(
            ServerPlayer intruder, ServerLevel level, BlockPos pos, IntrusionRecord.Action action
    ) {
        IntrusionRecord record = IntrusionAnalyzer.analyze(intruder, level, pos, action);
        if (record == null) return;  // not in a claim, or self-team, or cooldown
        notifyTeam(intruder.getServer(), record);
    }

    private void notifyTeam(MinecraftServer server, IntrusionRecord record) {
        if (server == null || record.recipients().isEmpty()) return;
        Component msg = IntrusionReport.format(record);
        for (var recipientUuid : record.recipients()) {
            ServerPlayer member = server.getPlayerList().getPlayer(recipientUuid);
            if (member != null) member.sendSystemMessage(msg);
        }
    }

    /** cooldown check + record (= true なら通知して良し、false なら直近通知済み)。 */
    static boolean shouldAlert(String key) {
        long now = System.currentTimeMillis();
        Long last = LAST_ALERT.get(key);
        if (last != null && now - last < COOLDOWN_MS) return false;
        LAST_ALERT.put(key, now);
        return true;
    }
}
