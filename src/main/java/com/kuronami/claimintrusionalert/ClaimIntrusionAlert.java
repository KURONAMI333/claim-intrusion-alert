package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.intrusion.IntrusionListener;
import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — entry point (NeoForge 1.21.1).
 *
 * <p>claim MOD (FTB Chunks / Open Parties and Claims) が守っているチャンクで、非メンバーが
 * ブロックの破壊・設置・右クリックを試みた瞬間に、claim 所有者とその team / party の
 * メンバーへ chat 通知を送る。オンラインなら即時、オフラインなら次回ログイン時の digest で。
 *
 * <p>本家 FTB Chunks は妨害通知を侵入者本人にだけ送り、所有者には届かない
 * (FTBTeam/FTB-Mods-Issues #257 でメンテナが "no longer planned" と恒久却下)。
 * OPAC も同じで、所有者側の通知オプションを持たない。その gap を埋める。
 */
@Mod(ClaimIntrusionAlert.MOD_ID)
public class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ClaimIntrusionAlert(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::onCommonSetup);
        NeoForge.EVENT_BUS.register(new IntrusionListener());
    }

    /** 全 MOD のロードが終わってから、利用できる claim MOD を確定させる。 */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        ClaimProviders.init();
    }
}
