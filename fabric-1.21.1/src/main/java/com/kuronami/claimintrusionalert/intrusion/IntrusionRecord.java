package com.kuronami.claimintrusionalert.death;

import net.minecraft.network.chat.Component;

/**
 * One reconstructed death. Everything here comes straight from vanilla
 * {@code DamageSource} / world state at the instant of death — no
 * guessing, no modded attribution heuristics. Same discipline as Lag
 * Whisperer: only state what is certain.
 *
 * <p>{@code deathMessage} is vanilla's own death Component captured at
 * death time and replayed later, so it re-localizes on whoever's client
 * reads it (a Japanese player sees the Japanese cause even on an English
 * server) without us re-implementing every damage type.
 *
 * @param deathMessage   vanilla's localized death message Component
 * @param x,y,z          block position of death
 * @param dimension      short dimension name ("overworld")
 * @param day            in-world day number at death
 * @param killerType     pretty entity-type of the responsible entity, or
 *                        empty if the death was environmental
 * @param killerDistance blocks between victim and killer at death, or -1
 * @param killerDir      8-wind compass direction to the killer, or empty
 * @param nearbyHostiles hostile mobs within 16 blocks at death
 */
public record IntrusionRecord(
        Component deathMessage,
        int x, int y, int z,
        String dimension,
        long day,
        String killerType,
        int killerDistance,
        String killerDir,
        int nearbyHostiles) {

    public boolean hasKiller() {
        return killerType != null && !killerType.isEmpty();
    }
}
