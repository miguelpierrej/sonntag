# Sonntag

[Español](README.md) · [Português](README.pt-BR.md) · **English**

Desktop application for organizing a congregation's meeting schedules: weekend talks,
midweek program, publishers, audio/video, attendants and the cleaning roster. Everything
is stored on your own computer, and every schedule can be exported as PDF or PNG for
printing or sharing.

> Independent project, with no official affiliation or endorsement from any organization.

## Features

- **Dashboard** — next meeting, the week's cleaning group, and meetings whose program is
  still incomplete over the next 28 days.
- **Weekend program** — talk title, speaker, chairman, study conductor and reader. Export
  a single meeting or the whole month (PDF/PNG).
- **Midweek program (S-140)** — full form: treasures, ministry, Christian living, songs
  and prayers. Export the program and print the assignment slips (S-89).
- **Publishers** — the roster of names used across every assignment.
- **Audio/video and attendants** — audio, video, stage, microphones and attendants per
  meeting, with PDF export.
- **Cleaning** — responsible group per week, with monthly PDF/PNG export.
- **Imports** — the meeting workbook (`mwb`, a PDF) fills in the midweek program; the
  **S-34** (`.jwpub`) loads all 194 public talk outlines into a pick list on the talk
  title field.
- **Sharing data** — under Settings › Data you export the blocks you choose to an
  encrypted `.sonntag` file (password optional) that another installation imports.
  Nothing is written before you review a summary, and you decide record by record what
  to do when both sides changed.
- **Offline** — no internet connection or account required; data never leaves the machine.

## Installation

Download the latest installer from the [Releases](../../releases) page:

| System | File |
| ------ | ---- |
| Windows | `Sonntag-<version>.msi` |
| Linux (Debian/Ubuntu) | `sonntag_<version>_amd64.deb` |
| Android (phone and tablet) | `Sonntag-<version>-debug.apk` |

On Windows the installer creates a Start menu shortcut and asks whether you want one on
the desktop. On Linux: `sudo dpkg -i sonntag_<version>_amd64.deb`. On Android, open the
APK on the device and allow installation from unknown sources.

## Getting started

1. The first launch opens the **initial setup**: congregation name (required), address,
   phone, and the **meeting days and times**.
2. Once the days are set, the app generates the meetings for the next 12 months.
3. Add your **publishers** and, under Settings, the **cleaning groups**.
4. Optional: use **Import S-34** on the weekend screen to get the outline list, and
   **Import workbook** on the midweek screen to fill in the weeks of the month.
5. To work on more than one device, export your data under **Settings › Data** and import
   it on the other one. Each installation keeps its own; the file is the bridge.

## Language

The app speaks **Spanish** and **Brazilian Portuguese**. On first launch it follows the
operating system language; anything else falls back to Spanish. You can change it at any
time under **Settings › General › Language**, and that choice overrides the system.
Exported documents follow the selected language.

## Your data

Everything lives in a single SQLite file:

```
Linux/macOS   ~/.salao-app/data.db
Windows       C:\Users\<user>\.salao-app\data.db
```

To back up or move to another computer, copy that file. Uninstalling the app does not
delete it.

## Development

Requires **JDK 17**. Kotlin Multiplatform with Compose Multiplatform (desktop/JVM only).

```shell
./gradlew :composeApp:run            # run
./gradlew :composeApp:packageDeb     # .deb package (on Linux)
./gradlew :composeApp:packageMsi     # .msi installer (on Windows)
./gradlew :composeApp:packageMsiWithPrompt  # .msi that prompts for shortcuts (the released one)
./gradlew :composeApp:exportAppIcon  # regenerate the icon from AppIcon.kt
./gradlew assembleDebug              # Android APK (requires the Android SDK)
```

Installers can only be produced on the target system — jpackage does not cross-compile.

The APK is signed with `composeApp/debug.keystore`, versioned here on purpose: with a
fixed key, a new build installs over the previous one without wiping the data. It is not
suitable for store publishing — the password is in plain sight.

| Component | Purpose |
| --------- | ------- |
| Compose Multiplatform | user interface |
| SQLDelight + SQLite (JDBC/HikariCP) | local database |
| Koin | dependency injection |
| Voyager | screens |
| Apache PDFBox | PDF export and `mwb` workbook parsing |

Layout: `composeApp/src/commonMain` holds the UI, ViewModels, repositories and
translations; `composeApp/src/jvmMain` holds the desktop-specific parts (database, PDF,
imports, icon, window).

### Publishing a release

The flow is triggered by the commit message. Add the version's section to
[`CHANGELOG.md`](CHANGELOG.md), commit it as **`Fecha versão <x.y.z>`** and push to
`master`: the workflow reads the version from the message, validates the changelog,
builds the `.msi` and `.deb`, creates the `v<x.y.z>` tag and publishes the Release with
those notes.

## License

Released under the [MIT](LICENSE) license.
