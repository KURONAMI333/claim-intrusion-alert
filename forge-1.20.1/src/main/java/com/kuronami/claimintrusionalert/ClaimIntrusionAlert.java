package com.kuronami.claimintrusionalert;

import com.kuronami.claimintrusionalert.command.ClaimIntrusionAlertCommand;
import com.kuronami.claimintrusionalert.death.IntrusionListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claim Intrusion Alert — entry point (Forge 1.20.1).
 *
 * <p>The vanilla death message says "you blew up". This says <em>what</em>
 * blew you up, exactly where, from which direction and how far, with how
 * many hostiles around, on what day — so you can avoid it next time.
 *
 * <p>Two registrations, nothing else: a {@link IntrusionListener} (vanilla
 * {@code LivingDeathEvent}) and the {@code /howdididie} command. No
 * mixin, no config, no registered game object, no passive tick task —
 * it does nothing until someone dies and then asks.
 *
 * <p>Forge 47.x (1.20.1) uses a no-arg {@code @Mod} constructor; only
 * the game event bus is needed here.
 */
@Mod(ClaimIntrusionAlert.MOD_ID)
public class ClaimIntrusionAlert {

    public static final String MOD_ID = "claimintrusionalert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ClaimIntrusionAlert() {
        LOGGER.info("Claim Intrusion Alert ready — run /howdididie after a death.");
        MinecraftForge.EVENT_BUS.register(new IntrusionListener());
        MinecraftForge.EVENT_BUS.register(new ClaimIntrusionAlertCommand());
    }
}
