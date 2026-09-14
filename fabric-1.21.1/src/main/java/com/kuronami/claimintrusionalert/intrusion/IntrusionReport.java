package com.kuronami.claimintrusionalert.intrusion;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@link IntrusionRecord} を 1 行の chat メッセージに整形。
 *
 * <p>例: <code>⚠ Alex tried to break a block at (123, 64, -88) in minecraft:overworld.</code>
 *
 * <p>lang キーは行為ごとに 1 本（{@code claimintrusionalert.alert.break} /
 * {@code .place} / {@code .interact}）。引数は位置指定で
 * {@code %1$s} = 侵入者名 / {@code %2$s}〜{@code %4$s} = X, Y, Z / {@code %5$s} = dimension ID。
 * 語順は言語ごとに lang ファイル側で組む。
 *
 * <p>digest（オフライン中の分をログイン時にまとめて出す）はヘッダ 1 行だけ専用キーを持ち、
 * 明細は上と同じ alert キーを再利用する。
 * クライアントに MOD が無い server-side 運用でも読めるよう、英語の fallback を同時に送る。
 */
public final class IntrusionReport {

    private IntrusionReport() {}

    /**
     * digest のヘッダ 1 行。明細はこの後に {@link #format(IntrusionRecord)} で続ける。
     *
     * @param count 保留していた件数（明細の表示上限とは無関係の総数）
     */
    public static Component digestHeader(int count) {
        return Component.empty()
                .append(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatableWithFallback(
                        "claimintrusionalert.digest.header",
                        "Intrusion attempts while you were away: %1$s", count)
                        .withStyle(ChatFormatting.GOLD));
    }

    public static Component format(IntrusionRecord r) {
        return Component.empty()
                .append(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW))
                .append(Component.translatableWithFallback(
                        "claimintrusionalert.alert." + r.action().key(),
                        switch (r.action()) {
                            case BREAK -> "%1$s tried to break a block at (%2$s, %3$s, %4$s) in %5$s.";
                            case PLACE -> "%1$s tried to place a block at (%2$s, %3$s, %4$s) in %5$s.";
                            case INTERACT -> "%1$s tried to interact with a block at (%2$s, %3$s, %4$s) in %5$s.";
                        },
                        Component.literal(r.intruderName()).withStyle(ChatFormatting.AQUA),
                        r.pos().getX(),
                        r.pos().getY(),
                        r.pos().getZ(),
                        r.dimension().location().toString()
                ).withStyle(ChatFormatting.GRAY));
    }
}
