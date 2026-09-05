When someone tries to break, place, or interact inside your claim and the claim mod blocks them, you get a one-line chat message saying who it was, what they tried, and where.

Works with **FTB Chunks** and **Open Parties and Claims**. Either one is enough, both together is fine, and neither of them tells the claim owner anything — the blocked player sees "you can't do that", and the owner sees nothing at all.

```
⚠ Alex tried to break a block at (210, 64, -88) in minecraft:overworld.
```

**Offline? You still find out.** If nobody from the team or party is online when it happens, the attempts are stored and posted to you the next time you log in. Up to 20 per player, kept for 7 days.

```
Intrusion attempts while you were away: 3
```

Teammates and allies never trigger an alert — the mod asks the claim mod itself who is allowed to be there, rather than guessing. The same intruder hitting the same chunk is reported once every 5 minutes, so a determined griefer can't flood your chat.

**Install**

1. Install FTB Chunks or Open Parties and Claims (or both).
2. Drop this in your `mods` folder, server side.

No config file, no commands, nothing to set up. Output is localized in 9 languages.

**Dependencies**

- [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge) — optional (CurseForge only; FTB Chunks is not published on Modrinth)
- [Open Parties and Claims](https://modrinth.com/mod/open-parties-and-claims) — optional

At least one of the two must be installed. With neither, the mod logs one line at startup and stays idle.

**Scope and limitations**

- Alerts are delivered as chat messages; the mod does not add an overlay.
- Covers block break, block place, and block interaction. Entity interactions and item use are not reported.
- Reports what the claim mod *would* block, so an attempt another mod cancels first is still reported as an attempt.
- 0.1.0 never actually sent an alert: it waited for a cancelled event that the mod loader does not deliver to it. If you are running 0.1.0, it has been silent the whole time — 0.2.0 is what makes it work.

Bugs and questions: comment on the CurseForge page, or DM @kuronami333 on X.

All Rights Reserved. Modpack inclusion is allowed without permission or credit. Source: https://github.com/KURONAMI333/claim-intrusion-alert
