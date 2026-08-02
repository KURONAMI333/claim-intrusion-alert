package com.kuronami.claimintrusionalert.provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;
import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * FTB Chunks アダプタ。
 *
 * <p>FTB Chunks は maven（{@code https://maven.ftb.dev/releases}）から取れるので
 * 型を直接使う。ただしクラスに触れるのは {@link HostMods#isLoaded(String)} が true の
 * ときだけ（未導入環境で {@link NoClassDefFoundError} を出さないため）。
 *
 * <p>判定: claim あり かつ 行為者がチームメンバーでも ally でもない = 阻止される。
 * ally を除外しないと、FTB Chunks が通す行為まで侵入として通知してしまう。
 *
 * <p>オフライン member の列挙だけは FTB Teams の {@code Team#getMembers()} に降りる。
 * FTB Teams は本 repo のどのセルでもコンパイル依存に入れていない（推移依存を切っているため）
 * ので、そこだけリフレクションで叩き、解決できなければ digest を諦めてオンライン通知だけ続ける。
 */
public final class FtbChunksProvider implements ClaimProvider {

    public static final String MOD_ID = "ftbchunks";

    private boolean present;
    private boolean presenceChecked;
    private boolean warned;

    private boolean memberHandlesResolved;
    @Nullable
    private MemberHandles memberHandles;

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public boolean isAvailable() {
        if (!presenceChecked) {
            present = HostMods.isLoaded(MOD_ID);
            presenceChecked = true;
        }
        return present;
    }

    @Override
    @Nullable
    public ClaimHit resolve(ServerPlayer actor, ServerLevel level, BlockPos pos, IntrusionRecord.Action action) {
        try {
            ChunkDimPos cdp = new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
            ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(cdp);
            if (chunk == null) return null;

            ChunkTeamData teamData = chunk.getTeamData();
            UUID actorUuid = actor.getUUID();
            if (teamData.isTeamMember(actorUuid) || teamData.isAlly(actorUuid)) return null;

            MinecraftServer server = actor.getServer();
            if (server == null) return null;

            List<UUID> online = new ArrayList<>();
            List<UUID> absent = new ArrayList<>();
            for (UUID candidate : candidates(teamData, server)) {
                if (candidate.equals(actorUuid)) continue;
                // 通知して良いかの権威は isTeamMember に置く。候補の出所（getMembers）が
                // ally や招待中を含んでいても、ここで落ちるので誤通知にならない。
                if (!teamData.isTeamMember(candidate)) continue;
                if (server.getPlayerList().getPlayer(candidate) != null) online.add(candidate);
                else absent.add(candidate);
            }
            // FTB Chunks の公開 API は claim をチーム単位で持ち、所有者 UUID を出さない。
            return new ClaimHit(MOD_ID, null, List.copyOf(online), List.copyOf(absent), true);
        } catch (Throwable t) {
            report(t);
            return null;
        }
    }

    /**
     * 宛先の候補集合。チーム全 member（取れれば）とオンライン全員の和。
     *
     * <p>オンライン全員を常に混ぜるのは、FTB Teams のハンドルが解決できない環境でも
     * オンライン通知が従来どおり動くようにするため。絞り込みは呼び出し側の isTeamMember。
     */
    private Set<UUID> candidates(ChunkTeamData teamData, MinecraftServer server) {
        Set<UUID> out = new LinkedHashSet<>();
        Set<UUID> members = allMembers(teamData);
        if (members != null) out.addAll(members);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) out.add(p.getUUID());
        return out;
    }

    /** FTB Teams の {@code Team#getMembers()}（オフライン含む）。取れなければ null。 */
    @Nullable
    private Set<UUID> allMembers(ChunkTeamData teamData) {
        MemberHandles h = memberHandles();
        if (h == null) return null;
        try {
            Object team = h.getTeam.invoke(teamData);
            if (team == null) return null;
            Object members = h.getMembers.invoke(team);
            if (!(members instanceof Set<?> set)) return null;
            Set<UUID> out = new LinkedHashSet<>();
            for (Object o : set) {
                if (o instanceof UUID uuid) out.add(uuid);
            }
            return out;
        } catch (Throwable t) {
            report(t);
            return null;
        }
    }

    /**
     * ハンドルの解決は 1 回だけ試す。失敗しても provider は止めない
     * （digest が出なくなるだけで、オンライン通知は動き続ける）。
     */
    @Nullable
    private MemberHandles memberHandles() {
        if (!memberHandlesResolved) {
            memberHandlesResolved = true;
            try {
                memberHandles = new MemberHandles(
                        ChunkTeamData.class.getMethod("getTeam"),
                        Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getMembers"));
            } catch (Throwable t) {
                ClaimIntrusionAlert.LOGGER.warn(
                        "FTB Teams member listing is unavailable — offline digests stay off for FTB Chunks "
                                + "claims, live alerts keep working: {}", t.toString());
                ClaimIntrusionAlert.LOGGER.debug("FTB Teams reflection resolution detail", t);
            }
        }
        return memberHandles;
    }

    /**
     * 呼び出し時の例外は「今回の判定を諦める」だけで、provider を止めない。
     * 一過性の失敗でセッション中ずっと無言になるのを避ける（無言の停止こそが v0.1.0 の欠陥だった）。
     * ログは初回だけ warn、以降は debug（既定では出ない）。
     */
    private void report(Throwable t) {
        if (!warned) {
            warned = true;
            ClaimIntrusionAlert.LOGGER.warn("FTB Chunks API call failed: {}", t.toString());
        }
        ClaimIntrusionAlert.LOGGER.debug("FTB Chunks API failure detail", t);
    }

    /** FTB Teams 側だけのリフレクションハンドル。{@code ChunkTeamData#getTeam} → {@code Team#getMembers}。 */
    private record MemberHandles(Method getTeam, Method getMembers) {}
}
