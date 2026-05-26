package com.kuronami.claimintrusionalert.intrusion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Chunks API を叩いて claim 情報を引き、通知すべきか / 誰に通知するかを判定する。
 *
 * <p>FTB Chunks API drift で落ちないように、全体を try/catch で守る (silent fail で
 * vanilla 動作を阻害しない、C2MX/P2MX の warnApiDriftOnce 流派)。
 */
public final class IntrusionAnalyzer {

    private IntrusionAnalyzer() {}

    /**
     * @return null なら通知不要 (claim 外 / 同チーム / cooldown 中 / API 取れず)
     */
    public static IntrusionRecord analyze(
            ServerPlayer intruder,
            ServerLevel level,
            BlockPos pos,
            IntrusionRecord.Action action
    ) {
        try {
            ChunkDimPos cdp = new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
            ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(cdp);
            if (chunk == null) return null;

            ChunkTeamData teamData = chunk.getTeamData();
            UUID intruderUuid = intruder.getUUID();
            if (teamData.isTeamMember(intruderUuid)) return null;

            // cooldown check
            String key = intruderUuid + "@" + cdp.dimension().location() + ":" + cdp.x() + "," + cdp.z();
            if (!IntrusionListener.shouldAlert(key)) return null;

            // recipients = team members (online のみ、PlayerList から逆引き)
            MinecraftServer server = intruder.getServer();
            if (server == null) return null;
            List<UUID> recipients = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (teamData.isTeamMember(p.getUUID())) {
                    recipients.add(p.getUUID());
                }
            }
            if (recipients.isEmpty()) return null;  // owner offline = (将来 digest)

            return new IntrusionRecord(
                    intruderUuid,
                    intruder.getName().getString(),
                    action,
                    level.dimension(),
                    pos,
                    System.currentTimeMillis(),
                    recipients
            );
        } catch (Throwable t) {
            // FTB Chunks API drift で silent fail
            ClaimIntrusionAlert.LOGGER.debug("FTB Chunks API call failed: {}", t.toString());
            return null;
        }
    }
}
