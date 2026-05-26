package com.kuronami.claimintrusionalert.intrusion;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@link IntrusionRecord} を 1 行の chat メッセージに整形。
 *
 * <p>例: <code>⚠ Alex tried to break a block at (123, 64, -88) in overworld.</code>
 *
 * <p>vanilla translatable key を使うので、各言語の lang ファイル
 * (en_us.json / ja_jp.json) で文言を差し替え可。
 */
public final class IntrusionReport {

    private IntrusionReport() {}

    public static Component format(IntrusionRecord r) {
        String dim = r.dimension().location().toString();
        String actionKey = "claimintrusionalert.action." + r.action().key();
        return Component.empty()
                .append(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatable(
                        "claimintrusionalert.alert",
                        Component.literal(r.intruderName()).withStyle(ChatFormatting.AQUA),
                        Component.translatable(actionKey),
                        r.pos().getX(),
                        r.pos().getY(),
                        r.pos().getZ(),
                        dim
                ).withStyle(ChatFormatting.GRAY));
    }
}
