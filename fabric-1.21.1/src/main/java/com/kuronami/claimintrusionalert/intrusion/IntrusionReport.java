package com.kuronami.claimintrusionalert.death;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Renders a {@link IntrusionRecord} into styled chat lines. Shared by the
 * automatic on-death post and the {@code /howdididie} re-view, so both
 * look identical and the wording lives in one place.
 *
 * <p>The report is shown automatically the moment you die (well-styled,
 * color-coded) rather than hidden behind a command — you shouldn't have
 * to know a command exists to learn what just killed you.
 */
public final class IntrusionReport {

    private IntrusionReport() {}

    /** Ordered, pre-styled lines. Every string is {@code translatable}. */
    public static List<Component> render(IntrusionRecord r) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("claimintrusionalert.title")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        out.add(Component.literal(" ┃ ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable("claimintrusionalert.cause", r.deathMessage())
                .withStyle(ChatFormatting.RED)));
        out.add(Component.literal(" ┃ ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.translatable("claimintrusionalert.where",
                r.x(), r.y(), r.z(), r.dimension(), r.day())
                .withStyle(ChatFormatting.YELLOW)));
        if (r.hasKiller()) {
            out.add(Component.literal(" ┃ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("claimintrusionalert.killer",
                    r.killerType(), r.killerDistance(),
                    Component.translatable("claimintrusionalert.dir." + r.killerDir()))
                    .withStyle(ChatFormatting.GOLD)));
        }
        out.add(Component.literal(" ┃ ").withStyle(ChatFormatting.DARK_GRAY)
            .append(r.nearbyHostiles() > 0
                ? Component.translatable("claimintrusionalert.nearby", r.nearbyHostiles())
                    .withStyle(ChatFormatting.GRAY)
                : Component.translatable("claimintrusionalert.nearby.none")
                    .withStyle(ChatFormatting.DARK_GRAY)));
        return out;
    }
}
