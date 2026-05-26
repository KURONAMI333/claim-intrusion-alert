package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.intrusion.IntrusionListener;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — entry point (NeoForge 1.21.1).
 *
 * <p>FTB Chunks の claim 内で非 ally プレイヤーが妨害アクション (block break / place /
 * interact) を試みて FTB Chunks にキャンセルされた瞬間、claim owner と team members の
 * オンラインプレイヤーに chat 通知を送る。
 *
 * <p>本家 FTB Chunks は妨害通知を侵入者本人にだけ送り、owner には届かない gap
 * (FTBTeam/FTB-Mods-Issues #257 でメンテナが "no longer planned" と恒久却下) を埋める。
 */
@Mod(ClaimIntrusionAlert.MOD_ID)
public class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ClaimIntrusionAlert(IEventBus modBus, ModContainer container) {
        LOGGER.info("Claim Intrusion Alert ready — listening for FTB Chunks intrusions.");
        NeoForge.EVENT_BUS.register(new IntrusionListener());
    }
}
