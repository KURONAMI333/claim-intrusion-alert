package com.kuronami.claimintrusionalert.provider;

import net.neoforged.fml.ModList;

/**
 * 他 MOD の存在確認。
 *
 * <p>ここだけが loader 固有 API に触れる。他ローダーへ移植するときは
 * {@link #isLoaded(String)} の中身 1 行だけを差し替える
 * （Fabric: {@code FabricLoader.getInstance().isModLoaded(modId)} /
 * Forge 1.20.1: {@code net.minecraftforge.fml.ModList}）。
 */
public final class HostMods {

    private HostMods() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
