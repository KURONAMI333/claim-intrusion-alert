package com.kuronami.claimintrusionalert.provider;

import java.util.ArrayList;
import java.util.List;

import com.kuronami.claimintrusionalert.ClaimIntrusionAlert;
import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * 利用可能な {@link ClaimProvider} の一覧と、順に問い合わせる入口。
 *
 * <p>このセル（Forge 1.21.1）限定の差分: {@code ALL} に FTB Chunks provider を含めない。
 * FTB Chunks は {@code maven.ftb.dev/releases} に Forge 1.21.1 向けの
 * {@code ftb-chunks-forge} / {@code ftb-library-forge} 座標を一度も公開していない
 * （実測 2026-08-02: 両アーティファクトの maven-metadata.xml に "21xx" 系バージョンが皆無、
 * 最新は 1.20.4 系の {@code 2004.x.x}）。コンパイル依存を作れないので provider ごと外す。
 * 他 3 セル（{@code src/} / {@code fabric-1.21.1} / {@code forge-1.20.1}）はこの制約が無い。
 */
public final class ClaimProviders {

    private static final List<ClaimProvider> ALL = List.of(new OpacProvider());

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
                    "No supported claim mod found (Open Parties and Claims). "
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
