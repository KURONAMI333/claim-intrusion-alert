package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.intrusion.IntrusionListener;
import com.kuronami.claimintrusionalert.provider.ClaimProviders;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — entry point (Forge 1.20.1)。
 *
 * <p>claim MOD (FTB Chunks / Open Parties and Claims) が守っているチャンクで、非メンバー・
 * 非 ally が ブロックの破壊・設置・右クリックを試みた瞬間に、claim 所有者とその team / party の
 * オンラインメンバーへ chat 通知を送る。ロジックは NeoForge 基準セルと同一（provider へ事前
 * 問い合わせ）。差分は Forge の event bus 取得方法と package だけ。
 */
@Mod(ClaimIntrusionAlert.MOD_ID)
public class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ClaimIntrusionAlert() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(new IntrusionListener());
    }

    /** 全 MOD のロードが終わってから、利用できる claim MOD を確定させる。 */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        ClaimProviders.init();
    }
}
