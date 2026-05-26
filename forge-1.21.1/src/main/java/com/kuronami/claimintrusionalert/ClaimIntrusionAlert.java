package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.intrusion.IntrusionListener;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — entry point (Forge 1.21.1).
 *
 * <p>FTB Chunks の claim 内で非 ally プレイヤーが妨害アクションを試みて FTB Chunks に
 * キャンセルされた瞬間、claim owner と team members に chat 通知。
 */
@Mod(ClaimIntrusionAlert.MOD_ID)
public class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ClaimIntrusionAlert(FMLJavaModLoadingContext context) {
        LOGGER.info("Claim Intrusion Alert ready — listening for FTB Chunks intrusions.");
        MinecraftForge.EVENT_BUS.register(new IntrusionListener());
    }
}
