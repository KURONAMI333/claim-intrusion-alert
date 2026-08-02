package com.kuronami.claimintrusionalert.provider;

import java.util.ArrayList;
import java.util.List;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;
import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/** 利用可能な {@link ClaimProvider} の一覧と、順に問い合わせる入口。 */
public final class ClaimProviders {

    private static final List<ClaimProvider> ALL = List.of(new FtbChunksProvider(), new OpacProvider());

    private static volatile List<ClaimProvider> active = List.of();

    private ClaimProviders() {}

    /** MOD ロード完了後に 1 回だけ呼ぶ。どのホストも無ければ warn 1 行を出して以降何もしない。 */
    public static void init() {
        List<ClaimProvider> found = new ArrayList<>();
        for (ClaimProvider p : ALL) {
            if (p.isAvailable()) found.add(p);
        }
        active = List.copyOf(found);
        if (active.isEmpty()) {
            ClaimIntrusionAlert.LOGGER.warn(
                    "No supported claim mod found (FTB Chunks / Open Parties and Claims). "
                            + "Claim Intrusion Alert will stay idle.");
        } else {
            ClaimIntrusionAlert.LOGGER.info("Claim Intrusion Alert is watching claims from: {}",
                    active.stream().map(ClaimProvider::id).toList());
        }
    }

    /** provider を順に試し、最初に「阻止される」と答えたものを返す。無ければ null。 */
    @Nullable
    public static ClaimHit resolveFirst(
            ServerPlayer actor, ServerLevel level, BlockPos pos, IntrusionRecord.Action action
    ) {
        for (ClaimProvider p : active) {
            if (!p.isAvailable()) continue;
            ClaimHit hit = p.resolve(actor, level, pos, action);
            if (hit != null && hit.wouldBlock()) return hit;
        }
        return null;
    }

    /** どの claim MOD も無い環境なら true（イベント購読ごと省ける）。 */
    public static boolean isIdle() {
        return active.isEmpty();
    }
}
