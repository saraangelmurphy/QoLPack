# QoL Pack

Campaign conveniences for **Starsector 0.98.5a**: automatic transponder control, scavenging, preliminary surveys, a real-world clock, and smoother ability use while travelling.

**Original creator and main author: [PureTilt](https://github.com/PureTilt/QoLPack).** This maintained version of PureTilt's QoL Pack is maintained by **saraangelmurphy**.

[![Install with TriOS](https://img.shields.io/badge/Install_with-TriOS-6750A4?style=for-the-badge)](https://trilink.wispborne.com/open.html?mod=%7B%22url%22%3A%22https%3A%2F%2Fraw.githubusercontent.com%2Fsaraangelmurphy%2FQoLPack%2Frefs%2Fheads%2Fmaster%2Fpt_qolpack.version%22%2C%22id%22%3A%22pt_qolpack%22%7D&dep=%7B%22id%22%3A%22lw_lazylib%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLazyWizard%2Flazylib%2Freleases%2Fdownload%2F3.0%2FLazyLib.3.0.zip%22%2C%22version%22%3A%223.0.0%22%7D)

## Features

- **Transponder Off in Hyperspace:** automatically turn off the fleet transponder when entering hyperspace.
- **Scavenge as You Fly:** enable the blue **Auto Scavenge** ability to collect from eligible debris fields while travelling. Automatic activation waits during menus, interactions and battles, and checks for nearby hostile fleets.
- **Better Active Sensor Burst:** keep Sustained Burn enabled while using Active Sensor Burst, with the ability's normal slow movement and sensor effects.
- **Better Remote Survey:** keep Sustained Burn enabled while using Remote Survey.
- **Campaign clock:** display your local real-world time below the campaign date, including while paused, with 12-hour and 24-hour formats.
- **Partial surveying:** automatically acquire preliminary survey data for nearby planets within the fleet's sensor-strength range.

## Installation

Requires **LazyLib 3.0 or newer**. **LunaLib** provides optional in-game settings. Supports **Java 17**.

1. Use **Install with TriOS** above, or download **QoLPack.zip** from [Releases](https://github.com/saraangelmurphy/QoLPack/releases).
2. Import the ZIP into TriOS, or extract it into your Starsector `mods` directory so `mods/QoLPack/mod_info.json` exists.
3. Enable **QoL Pack** and **LazyLib**.

Enable one copy of the mod at a time. The standalone **Transponder Off** mod conflicts with QoL Pack's included transponder feature.

Version Checker provides update notifications, direct release downloads and the [changelog](changelog.txt).

## Usage

Assign the blue **Auto Scavenge** ability to your ability bar and toggle it on. Its enabled state is retained when saving and loading.

The campaign clock, preliminary surveys and travel-ability improvements are enabled by default. Configure them through LunaLib's settings menu, or edit `QoLPack.ini`, then reload the campaign.

This version focuses on campaign features. The original shield helpers, combat indicators, order drawing and direction markers have been removed.

## Authors and attribution

- **PureTilt:** original creator and main author.
- **saraangelmurphy:** maintainer of this version.

Original project: [PureTilt/QoLPack](https://github.com/PureTilt/QoLPack).

**This maintained version will be removed upon request from the original author.**

<details>
<summary>AI use disclaimer</summary>

Some code and documentation changes in this maintained version were created using AI tools:

- **Code and documentation:** OpenAI Codex, used for compatibility updates, campaign-clock changes, bug fixes, and documentation.

This disclosure covers the maintenance work in this version; credit for the original mod remains with PureTilt.

</details>
