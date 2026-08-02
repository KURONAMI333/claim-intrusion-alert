package com.kuronami.claimintrusionalert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — 共有識別子（MOD_ID / LOGGER）。
 *
 * <p>loader のエントリポイントは {@link ClaimIntrusionAlertFabric}。この class は、
 * NeoForge 基準セルから byte 単位でコピーした provider パッケージが loader 固有 import 無しで
 * {@code ClaimIntrusionAlert.LOGGER} を参照し続けられるように分離してある。
 */
public final class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ClaimIntrusionAlert() {}
}
