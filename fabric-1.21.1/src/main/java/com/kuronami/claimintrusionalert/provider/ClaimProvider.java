package com.kuronami.claimintrusionalert.provider;

import com.kuronami.claimintrusionalert.intrusion.IntrusionRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * claim MOD 1 つ分のアダプタ。実装は {@link FtbChunksProvider} / {@link OpacProvider}。
 *
 * <p>判定は「事前問い合わせ」で行う。イベントがキャンセルされたかは見ない
 * （キャンセル済みイベントは NeoForge のバスでは配送されず、Fabric では後段リスナーが
 * 短絡されるため、観測方式は成立しない）。かわりに claim MOD へ
 * 「この行為はここで阻止されるか」を直接聞く。
 *
 * <p>実装は副作用を持ってはいけない（cooldown の消費・通知の送信は呼び出し側の責務）。
 */
public interface ClaimProvider {

    /** provider の識別子。ログ用。例: {@code "ftbchunks"} / {@code "openpartiesandclaims"} */
    String id();

    /** 対象の claim MOD がロードされていて、かつ API が生きているか。false なら以降呼ばれない。 */
    boolean isAvailable();

    /**
     * 指定の行為が claim に対する侵入かを判定し、通知に必要な情報を返す。
     *
     * @return 侵入でない / claim 外 / API 失敗 なら null
     */
    @Nullable
    ClaimHit resolve(ServerPlayer actor, ServerLevel level, BlockPos pos, IntrusionRecord.Action action);
}
