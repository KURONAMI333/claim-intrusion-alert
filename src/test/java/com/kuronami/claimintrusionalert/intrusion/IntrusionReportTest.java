package com.kuronami.claimintrusionalert.intrusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class IntrusionReportTest {

    @Test
    void alertHasReadableFallbackForClientWithoutMod() {
        for (IntrusionRecord.Action action : IntrusionRecord.Action.values()) {
            IntrusionRecord record = new IntrusionRecord(
                    UUID.randomUUID(), "Alex", action, Level.OVERWORLD,
                    new BlockPos(210, 64, -88), 0L, List.of(), List.of());
            Component message = IntrusionReport.format(record);
            TranslatableContents contents = assertInstanceOf(
                    TranslatableContents.class, message.getSiblings().get(1).getContents());
            assertEquals("claimintrusionalert.alert." + action.key(), contents.getKey());
            String verb = switch (action) {
                case BREAK -> "break";
                case PLACE -> "place";
                case INTERACT -> "interact with";
            };
            assertEquals("⚠ Alex tried to " + verb + " a block at (210, 64, -88) in minecraft:overworld.",
                    message.getString());
        }
    }

    @Test
    void digestHeaderHasReadableFallbackForClientWithoutMod() {
        Component header = IntrusionReport.digestHeader(3);
        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, header.getSiblings().get(1).getContents());
        assertEquals("claimintrusionalert.digest.header", contents.getKey());
        assertEquals("⚠ Intrusion attempts while you were away: 3", header.getString());
    }
}
