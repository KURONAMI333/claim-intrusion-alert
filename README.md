# Claim Intrusion Alert

> Someone tries to grief your claim and gets blocked — **you get told**. Name, action, coordinates. FTB Chunks and Open Parties and Claims both notify the intruder and nobody else.

[![License: All Rights Reserved](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey.svg)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-claim--intrusion--alert-00AF5C)](https://modrinth.com/mod/claim-intrusion-alert)
[![CurseForge](https://img.shields.io/badge/CurseForge-claim--intrusion--alert-F16436)](https://www.curseforge.com/minecraft/mc-mods/claim-intrusion-alert)

---

## What it does

When a player who isn't a member or ally tries to **break / place / interact** inside a claim, the claim owner and their team or party get a one-line chat message:

```
⚠ Alex tried to break a block at (210, 64, -88) in minecraft:overworld.
```

If nobody is online at the time, the attempts are stored and posted on next login:

```
Intrusion attempts while you were away: 3
```

- **Two hosts, one mod**: FTB Chunks and Open Parties and Claims. Either alone, or both.
- **Asks the host, doesn't guess**: whether an action would be blocked is decided by the claim mod's own rules, so teammates and allies never trigger an alert.
- **Offline digest**: up to 20 attempts per player, kept 7 days, delivered at next login.
- **5-minute cooldown** per (intruder, claim chunk) so chat can't be flooded.
- **Server-side, no config, no commands.** Output localized in 9 languages.

## Why

Neither host tells the owner. FTB Chunks says "you can't do that here" to the person who tried, and [FTB-Mods-Issues #257](https://github.com/FTBTeam/FTB-Mods-Issues/issues/257) asking for owner-side notification was marked **"no longer planned for FTB"**. Open Parties and Claims has no notification feature at all — every message its protection sends goes to the blocked player. Neither keeps a history of attempts, so if you were offline there is nothing to find afterwards.

## Supported loaders / versions

| Minecraft | NeoForge | Forge | Fabric |
|---|:---:|:---:|:---:|
| 1.21.1 | ✅ | — | ✅ |
| 1.20.1 | — | ✅ | ✅ |

Forge 1.21.1 is not published: neither host ships that combination.

## Dependencies

At least one of:

- **FTB Chunks** — optional
- **Open Parties and Claims** — optional

With neither installed the mod logs one line at startup and stays idle. Neither is a hard dependency, so a pack can ship it with either host.

## Install

1. Install the loader and one of the two claim mods.
2. Drop the jar for your loader/version into `mods/` — server side only.

## Note on 0.1.0

0.1.0 never sent an alert. It watched for a cancelled event, but NeoForge does not deliver cancelled events to a listener unless it asks for them (`@SubscribeEvent(receiveCanceled = true)`), and its listener ran at `HIGHEST` priority — before the claim mod cancelled anything. Both paths were dead. 0.2.0 replaces the mechanism: it asks the claim mod directly whether the action would be blocked, which also made Fabric support possible.

## License

[All Rights Reserved](LICENSE) — modpack inclusion welcome, no credit required. Source is published so you can read exactly what it does.

## Credits

- Author: KURONAMI
- FTB Chunks: public `FTBChunksAPI`
- Open Parties and Claims: public API via reflection (no compile-time dependency, since OPAC publishes no maven artifact)
