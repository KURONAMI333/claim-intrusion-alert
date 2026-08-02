package com.kuronami.claimintrusionalert.intrusion;

import java.util.List;
import java.util.UUID;

import com.kuronami.claimintrusionalert.provider.ClaimHit;
import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * 1 件の行為を {@link ClaimProviders} に問い合わせ、通知すべきかを決める。
 *
 * <p>claim MOD 固有の知識はここに置かない（provider 側にある）。ここが持つのは
 * 「誰にも届かないなら捨てる」「同じ侵入者 × 同じチャンクは cooldown で抑える」の 2 つだけ。
 * 記録をオンラインへ送るか digest へ積むかは {@link DigestDelivery} の担当。
 */
public final class IntrusionAnalyzer {

    private IntrusionAnalyzer() {}

    /**
     * @return null なら通知不要（claim 外 / 阻止されない行為 / 宛先が 1 人もいない / cooldown 中）
     */
    @Nullable
    public static IntrusionRecord analyze(
            ServerPlayer intruder,
            ServerLevel level,
            BlockPos pos,
            IntrusionRecord.Action action
    ) {
        ClaimHit hit = ClaimProviders.resolveFirst(intruder, level, pos, action);
        if (hit == null) return null;

        List<UUID> recipients = hit.recipients();
        List<UUID> absentRecipients = hit.absentRecipients();
        // 宛先が 1 人もいない（無所属の claim 等）ときだけ捨てる。オフラインの team / party member は
        // digest に積んで次回ログインで届くので、全員オフラインでも記録する価値がある。
        // cooldown を消費する前に落とす。消費すると宛先が現れた直後の 5 分が黙る。
        if (recipients.isEmpty() && absentRecipients.isEmpty()) return null;

        if (!IntrusionListener.shouldAlert(cooldownKey(intruder, level, pos))) return null;

        return new IntrusionRecord(
                intruder.getUUID(),
                intruder.getName().getString(),
                action,
                level.dimension(),
                pos,
                System.currentTimeMillis(),
                recipients,
                absentRecipients
        );
    }

    /**
     * cooldown キー = (侵入者 UUID, dimension, チャンク座標)。
     *
     * <p>provider id は含めない。FTB Chunks と OPAC が同居する環境で同じ破壊が 2 通知になるため。
     */
    private static String cooldownKey(ServerPlayer intruder, ServerLevel level, BlockPos pos) {
        return intruder.getUUID() + "@" + level.dimension().location()
                + ":" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4);
    }
}
