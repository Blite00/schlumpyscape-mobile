# SchlumpyScape Mobile

An Android app that runs a **2009Scape** (RuneScape 2009-era) game client on your phone, connected to any 2009Scape-compatible server.
It is the 2009Scape mobile launcher (Pojav-based) with a touch-friendly rework of the game client: bottom tab row, zoomed side panel,
one-finger camera and list scrolling, pinch zoom, big right-click menus, a working world map, and a loading bar.

> Unofficial fan project. Not affiliated with Jagex or the 2009Scape team. See [NOTICE.md](NOTICE.md) for credits.

**Live example server:** `rs.schlumpy.net` (world 1, game port `43595`). The app in this repo points at it out of the box, so
you can build it, install it and log in right away. Make an account on the server's site: https://schlumpy.net
A prebuilt APK is on https://schlumpy.net/downloads.

---

## Install a prebuilt APK
1. Download the APK on your phone and open it. Allow "install unknown apps" for your browser/file manager when asked.
2. Start the app, wait for the first-launch unpack (about a minute), then log in.

Android 5+ with a 64-bit ARM phone (arm64) works best; x86 images (emulators) are included too.

## Connect it to YOUR server
The app reads one file, `config.json`, to know where to connect:

```json
{
    "ip_management": "play.example.com",
    "ip_address": "play.example.com",
    "world": 1,
    "server_port": 43594,
    "wl_port": 43595,
    "js5_port": 43595,
    "mouseWheelZoom": true,
    "pluginsFolder": "plugins"
}
```

| field | meaning |
| --- | --- |
| `ip_management` / `ip_address` | host name or IP of your server (the same host for a normal single-world server) |
| `world` | world number. The game port is `server_port + world`, so world 1 = `43595` |
| `server_port` | base port, `43594` on a stock 2009Scape server |
| `wl_port`, `js5_port` | the world-list and cache (JS5) ports. On 2009Scape these are `43595` for world 1 (i.e. `43594 + world`) |

The server must accept connections on those TCP ports from the phone: open/forward them on your router and firewall
(`43594`+`world` is the one that matters; a stock server listens on `43595` for world 1). Use a DNS name rather than your home IP if it changes.

Pick whichever way suits you:

**A. At install time (simplest for sharing):** edit `launcher/app_pojavlauncher/src/main/assets/config.json`, then build (below).
The file is copied to the app's data folder the first time the app runs and never overwritten after that.

**B. On the phone, no rebuild:** save a `config.json` like the one above onto the phone, open the app's launcher screen, open the bottom
sheet and tap **Load Config File**, choose your file, then fully restart the app. (Starting from a first install, do this before logging in.)
An example is in [config.example.json](config.example.json).

**C. Home Wi-Fi fix (optional):** many home routers cannot reach their own public name from inside (no "hairpin NAT"), so the phone cannot
connect while on your Wi-Fi. If you set the JVM properties `-Drs.publicHost=play.example.com -Drs.lanHost=192.168.1.50`
(add two `javaArgList.add(...)` lines next to `-DconfigFile` in `launcher/.../Tools.java`) the client tries the public name first and falls back
to the LAN address. If unset, the client just uses the host in `config.json`.

If you do not run a server yet: https://gitlab.com/2009scape/2009scape has the server and setup instructions (it also serves the cache the client
downloads on first connect).

## Build it yourself
You need: Linux/macOS, the Android SDK (platform 33 + build-tools, and NDK 25.2.9519653, which builds the small native pieces), JDK 8 (for the game client) and JDK 11 or 17 (for the launcher app).

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA8_HOME=/path/to/jdk8
export JAVA11_HOME=/path/to/jdk11
./build.sh        # -> schlumpyscape-android.apk
```

The script builds `client-mobile` into `rt4.jar`, drops it into the launcher's assets, then builds the launcher APK.
Without a key set up it signs with the standard Android debug key, which is fine for sideloading.

### Signing a release with your own key
```bash
keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000
RS_KEYSTORE=$PWD/release.jks RS_KEYSTORE_PASSWORD=yourpassword RS_KEY_ALIAS=release ./build.sh
```
Never commit the `.jks` (it is in `.gitignore`).

## Layout
```
client-mobile/   the RT4 game client fork (Java 8): mobile layout, touch input, GL fixes. Built to assets/rt4.jar
launcher/        the Android app (Pojav based): runs a bundled JRE, renders the client, handles touch -> mouse
build.sh         client jar + APK in one go
config.example.json
```
Handy places to look if you want to tinker: `client-mobile/client/src/main/java/rt4/MobileLayout.java` (screen layout),
`MobileInput.java` (touch gestures), `launcher/.../GLFWGLSurface.java` (taps and long press), `launcher/.../MyDialogFragment.java` (config/plugin loading).

## Controls
Tap = left click, long press (about 0.65 s) = right click menu, drag = scroll lists / turn the camera / pan the world map, pinch = zoom.

## Licence
GNU GPL v3, see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
