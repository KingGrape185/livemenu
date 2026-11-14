# Live Main Menu (Fabric, Minecraft 26.3)

Replaces the title screen with a playable 2D world.

- Move / jump / sprint: your normal Minecraft keys
- Left click: mine, Right click: place, 1-6: pick block
- Click pigs, the sun and moon, and the MINECRAFT title
- Play button: bottom right (opens world select)
- Hotkey (default P, rebindable in Controls): Singleplayer / Multiplayer / Options / Quit panel

## Build
Needs JDK 25 and Gradle 9.x.

    gradle wrapper --gradle-version 9.7.1
    ./gradlew build        # jar in build/libs/
    ./gradlew runClient    # test it

Requires Fabric Loader >= 0.19.3 and Fabric API 0.161.0+26.3.

## Parties
Open with the **Party** button (title screen), the menu panel, or the **O** key anywhere (rebindable).

- **Leader:** enter the address friends can reach you at (LAN IP, VPN IP like Tailscale/ZeroTier/Radmin, or public IP) -> *Create party* -> *Copy party code* and share it.
  Then choose where to play: type a server address / cycle *Next saved server* -> *Set server*, or *Host a world...* (pick a singleplayer world; it is opened to LAN and the party is told).
- **Members:** paste the code -> *Join party*. Auto-join (on by default) connects you from the title screen whenever the leader picks a destination; otherwise press *Join the leader now*.
- Ports: the party uses TCP 25599 on the leader's machine. For *Host a world*, the LAN port shown in the Party screen must also be reachable. Over the internet that needs port forwarding or a VPN.

## Hub (hamburger button)
The three-line button at the top-left of the title screen opens a dark three-panel hub:
**Friends** (add / favorite / remove, search, invite to party), **Party**, **Settings**, **Extras**.
The menu hotkey (default P) opens the hub on the Extras tab (Singleplayer, Multiplayer, Options, Quit).
Friends are stored in `config/livemenu-friends.txt`, settings in `config/livemenu.properties`.

Essential: if it is installed the hub only shows a notice. No public Essential friends API could be verified
(and its licence forbids reusing its code), so the friends lists are not shared.
