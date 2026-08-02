package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.intrusion.IntrusionListener;
import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Claim Intrusion Alert — entry point (Fabric 1.20.1)。
 *
 * <p>claim MOD (FTB Chunks / Open Parties and Claims) が守っているチャンクで、非メンバー・
 * 非 ally が ブロックの破壊・設置・右クリックを試みた瞬間に、claim 所有者とその team / party の
 * オンラインメンバーへ chat 通知を送る。ロジックは NeoForge 基準セルと同一で、
 * {@link IntrusionListener} が provider へ「この行為はここで阻止されるか」を事前問い合わせる。
 */
public final class ClaimIntrusionAlertFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        IntrusionListener.register();
        // 他 MOD のロード判定に onInitialize は早すぎる可能性があるため SERVER_STARTING で確定させる。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ClaimProviders.init());
    }
}
