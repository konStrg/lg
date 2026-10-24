# Transperceneige - Loup-Garou Minecraft Plugin

## Problem
Repo is empty. Single source of truth for roles and timeline: `bite.md` (the PDF is IGNORED, jobs/badges dropped).
Build a Paper plugin (MC 26.3, Java 25, Maven): train-themed social-deduction game with cabins, wagons,
night "hunts", day events and votes, inventory GUIs, chat/titles.

## Decisions
- Paper API, Maven. Package `com.example.loupgarou` (adjustable).
- Single arena, user-built train map; admin sets locations by command (wagons, one cabin per player, stations, debate wagon...).
- In-game text in French (messages.yml). Min 5 players.
- Out of scope v1: multiple arenas, DB/stats, resource pack/audio.

## Player classes
- Each player is assigned a character class (1st, 2nd, 3rd) = social tier, with an individual cabin position per class. No jobs, no badges.

## Roles (from bite.md)
**Green**
- Voyageur: base role, no power, votes and takes part in events
- Medecin legiste: heals the player killed by the Cannibal, 1 use, power must happen AFTER the death
- Barricadeur: barricades one cabin (protects from Cannibal) or a whole wagon; not two nights in a row
- Radio-amateur: listens/watches one cabin per night, only during a Cannibal hunt
- Veteran: can defend himself during the Cannibal night with a weaker weapon than the Cannibals'
- Kamikaze: can sacrifice himself to kill a Cannibal (dies in the process)
- Cupidon: links two players as lovers; if one dies the other dies too (the second lover hangs himself just before the next day's vote)

**Red**
- Cannibale: at night they debate the victim; the Cannibals may choose NOT to eat anyone. When a victim is chosen, one Cannibal is assigned the kill and gets a weapon; the others return to their cabins. The victim's cabin opens, Tom & Jerry style chase; the victim must die unless able to defend (Veteran, barricade, Kamikaze...). The night lasts until the victim is dead (plus a max night timer as safety).

**Grey**
- Pyromane: soaks one wagon per GREEN (odd) night only; after 3 soaked wagons he can start a fire and wins alone
- Ange: must be eliminated at the first player vote (wins alone)

## Timeline
- Odd days = vote days; even days = event days.
- Odd nights = green powers; even nights = red powers.
- Day 1: vote for the Capitaine.
- Night 1: green roles only.
- Day 2 (event): an event that, if completed, assigns a role. Red/green may be reassigned a Voyageur role if the player is already Voyageur. Probability of assigning red/green depends on players remaining and red/green roles already assigned (to define, config).
- Night 2: red roles only. Cannibal picks a victim or abstains; hunt starts (see Cannibale).
- Day 3: council vote on a player; the player is either confined to his cabin or ejected from the train. A confined player cannot take part in the next even day, even night and next odd night; he can take part again in the following odd day and night (interpretation of bite.md, confirm).
- Then loop: vote day / green night / event day / red night ...

## Initial role distribution
- Exactly one Grey is mandatory.
- Powered Greens/Reds: 30% -> 1 red/green, 15% -> 2, 1% -> 3 (bite.md; refers to green WITH power). Exact meaning to confirm; all values configurable.
- Everyone else Voyageur; Day 2 event can upgrade.

## Win conditions
- Greens win together when all Reds are dead.
- Reds win together only when every non-Red player (Greens and Greys) is dead.
- Pyromane wins alone with the fire; Ange wins alone if eliminated at the first vote. 

## Simple Voice Chat integration (optional)
- Simple Voice Chat has a Paper/Bukkit server plugin plus a Java API (`de.maxhenkel.voicechat:voicechat-api`, `VoicechatPlugin`). Our plugin declares it as `softdepend`, so the game works without it; players need the client mod.
- Features: mute/deaf players who are dead, confined to cabin or asleep at night; Cannibal night debate = private voice group (only awake Cannibals hear each other); cabin/wagon walls isolate proximity voice (only same cabin/wagon hear, via the API's audio-sender/microphone-packet events); Radio-amateur hears the targeted cabin's voices during a hunt; Ange/dead spectators optionally in a spectator group.
- Config toggles per feature; fully disabled if the API is absent. Must verify an API/plugin version compatible with MC 26.3.

## Architecture
- `game/`: GameManager, Game, GamePhase (day/night + number), PlayerData, WinChecker
- `arena/`: Arena, Wagon (cuboid), Cabin, setup commands
- `role/`: Role, Faction (GREEN/RED/GREY), one class per role, RoleDistributor
- `clazz/`: CharacterClass (FIRST/SECOND/THIRD), cabin assignment
- `event/`: day-2 event framework, role assignment probabilities
- `hunt/`: Cannibal hunt (weapon, cabin opening, timer, defenses)
- `gui/`: player selector, cabin/wagon selector, vote GUI
- `command/`: `/lg` + permissions; `listener/`: chat isolation, cabin locks, interaction locks, quit/rejoin

## Todos (tracked in SQL)
1. scaffold-maven: pom.xml, plugin.yml, main class, config/messages (FR)
2. arena-lobby: arena, wagons, cabins via commands, join/leave, countdown (min 5)
3. game-core: state machine, odd/even day/night timeline, timers, spectators
4. classes-cabins: 1st/2nd/3rd class assignment, cabin positions
5. roles: 10 roles + RoleDistributor (probabilities, mandatory Grey)
6. night-phase: green powers (odd nights), red powers (even nights), Cannibal hunt w/ timer, Pyromane soak, Cupidon links
7. day-vote: Capitaine vote, council vote, confinement/ejection rules, Ange check
8. day-events: Day-2 event + role assignment probabilities
9. win-conditions: Greens, Reds, Pyromane, Ange
10. world-effects: time/weather/titles/bossbar
11. commands-perms
12. tests-docs: JUnit (distribution, timeline, win logic), README, `mvn package`, manual Paper test
13. voice-chat: optional Simple Voice Chat integration (softdepend, groups, muting, cabin isolation, radio-amateur listening)

Dependencies: 2<-1, 3<-2, 4<-3, 5<-3,4, 6<-5, 7<-5, 8<-5, 9<-6,7, 10<-3, 11<-2, 12<-8,9,10,11.

## Notes
- Verified: Paper API `io.papermc.paper:paper-api:26.3.build.222-stable` (new scheme, not `-R0.1-SNAPSHOT`; latest stable at check time) on https://repo.papermc.io/repository/maven-public/. Simple Voice Chat lists MC 26.3 support (Modrinth); API `de.maxhenkel.voicechat:voicechat-api:2.6.25` on https://maven.maxhenkel.de/repository/public.
- Java 25 required; confirm exact pom coordinates when scaffolding (build number may need pinning/updating).
