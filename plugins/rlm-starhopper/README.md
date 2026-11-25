# Star Hopper

A RuneLite plugin that reads the Star Miners star list and suggests the next star and world to hop to, based on your filters.

- **Same spot first:** when the star you're mining depletes, it suggests the same location on another world (if "Prefer same location" is on).
- **Otherwise:** it suggests the highest estimated tier among the locations and world types you allow.
- **Old reports are aged:** about 1 tier per 7 minutes (configurable), so stale calls don't win.
- **Hopping:** a side panel lists every matching star with a **Hop** button. The on-screen box also has a **Hop to next star** option (right-click on desktop, long-press on mobile). Hops only happen when you tap; the plugin never hops on its own.
- **Notifications:** you get a notification with the next suggestion when your star depletes.

## Build the jar

In this repository (RuneLite Mobile) it's built with the other plugins by `tools/build-plugins.sh`
and bundled in the app, so there's nothing to import: it's in the plugin list in Developer mode.
To build it on its own:

### Option A: GitHub, from your phone
1. Create a new GitHub repository and upload everything in this folder, including the `.github` folder.
2. Open the **Actions** tab. The "Build plugin jar" workflow runs on every push, and you can also start it with **Run workflow**.
3. When it finishes, open the run and download the **star-hopper-jar** artifact. It's a zip that contains `star-hopper-1.0.0.jar`.

### Option B: a PC
With JDK 11 or newer and Gradle installed, run `gradle jar`. The jar is written to `build/libs/`.

## Install on RuneLite Mobile
Copy the jar to your phone, then go to **More → Plugins → Import .jar** in the app.

On desktop RuneLite, put the jar in `~/.runelite/sideloaded-plugins` and start RuneLite in developer mode.

## Setup
1. In the Star Miners Discord, run `/pluginkey`.
2. Open the Star Hopper settings and paste the key into **Star Miners feed → Authorization key**. The field is hidden, so the key isn't shown on screen.
3. Set your filters:
   - **Locations:** comma-separated words, e.g. `lumbridge, varrock, crafting guild`. Leave it empty to allow every location.
   - **Exclude locations:** defaults to `wilderness`.
   - World types, minimum tier, and whether to limit stars to your Mining level.

## If no stars show up
The Star Miners response format isn't published, so the parser accepts several common shapes. If the panel says "no stars were found":
1. Turn on **Log raw response**.
2. Look in the client log (`~/.runelite/logs/client.log`) for the line that starts with `Star Hopper raw response`.
3. Change the field-name lists at the top of `StarMinersClient.java` to match, or send the sample to whoever is helping you. Remove your key from it first.

## Notes
- The default refresh is every 60 seconds. Please don't set it much lower, because the Star Miners server is shared.
- This plugin sends your key and IP address to the Star Miners server, which isn't run by RuneLite. Ask the Star Miners admins before sharing the plugin with others.
