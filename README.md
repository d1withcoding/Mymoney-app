<a name="top"></a>
[![PennyWise AI Banner](banner.png)](https://github.com/sarim2000/pennywiseai-tracker)
[![GitHub stars](https://img.shields.io/github/stars/sarim2000/pennywiseai-tracker?style=social)](https://github.com/sarim2000/pennywiseai-tracker)
[![License](https://img.shields.io/badge/license-AGPL%20v3-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)](https://developer.android.com/about/versions/oreo/android-8.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF)](https://kotlinlang.org/)
[![Privacy](https://img.shields.io/badge/AI-100%25_On--Device-FF6B6B)](https://ai.google.dev/edge)
[![Downloads](https://img.shields.io/badge/Downloads-100+-green)](https://play.google.com/store/apps/details?id=com.pennywiseai.tracker)
[![F-Droid](https://img.shields.io/f-droid/v/com.pennywiseai.tracker?color=1976d2)](https://f-droid.org/packages/com.pennywiseai.tracker/)
[![GitHub release](https://img.shields.io/github/v/release/sarim2000/pennywiseai-tracker)](https://github.com/sarim2000/pennywiseai-tracker/releases)
[![GitHub last commit](https://img.shields.io/github/last-commit/sarim2000/pennywiseai-tracker)](https://github.com/sarim2000/pennywiseai-tracker/commits)
[![Discord](https://img.shields.io/badge/Discord-Join_Community-5865F2)](https://discord.gg/H3xWeMWjKQ)
[![Greptile: The War on Bugs](https://www.greptile.com/badge.svg)](https://www.greptile.com/?utm_source=oss_badge&utm_medium=readme&utm_campaign=greptile_for_open_source)

## PennyWise AI — Free & Open‑Source, private SMS‑powered expense tracker

Turn bank SMS into a clean, searchable money timeline with on-device AI assistance. Parsing, storage, and AI all run on your phone.


⭐ **Star us on GitHub — join 300+ supporters!**

[![Share](https://img.shields.io/badge/share-000000?logo=x&logoColor=white)](https://x.com/intent/tweet?text=Check%20out%20PennyWise%20AI%20-%20Privacy-first%20expense%20tracker%20with%20on-device%20AI:%20https://github.com/sarim2000/pennywiseai-tracker%20%23Android%20%23PrivacyFirst%20%23OnDeviceAI)
[![Share](https://img.shields.io/badge/share-0A66C2?logo=linkedin&logoColor=white)](https://www.linkedin.com/sharing/share-offsite/?url=https://github.com/sarim2000/pennywiseai-tracker)
[![Share](https://img.shields.io/badge/share-FF4500?logo=reddit&logoColor=white)](https://www.reddit.com/submit?title=PennyWise%20AI%20-%20Privacy-first%20expense%20tracker&url=https://github.com/sarim2000/pennywiseai-tracker)
[![Share](https://img.shields.io/badge/share-0088CC?logo=telegram&logoColor=white)](https://t.me/share/url?url=https://github.com/sarim2000/pennywiseai-tracker&text=Check%20out%20PennyWise%20AI)

## Overview

Your bank already texts you every transaction — PennyWise turns those SMS into a private, zero-setup expense tracker with on-device AI. No accounts, no sign-ups, no effort.

<a href="https://play.google.com/store/apps/details?id=com.pennywiseai.tracker">
  <img src="https://img.shields.io/badge/GET_IT_ON-Google_Play-00875F?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" />
</a>
<a href="https://apps.apple.com/app/id6793073167">
  <img src="https://img.shields.io/badge/Download_on_the-App_Store-0D96F6?style=for-the-badge&logo=apple&logoColor=white" alt="Download on the App Store" />
</a>
<a href="https://f-droid.org/packages/com.pennywiseai.tracker">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>

### How it works

1. **Grant SMS permission** (read-only) — no account creation, no inbox changes, no messages sent.
2. **PennyWise parses your transactions instantly** — extracts amount, merchant, category, and date. New SMS are detected in real-time.
3. **Get insights** — analytics, budgets, subscriptions, and an on-device AI assistant you can ask anything.

📖 **New here?** The [user guide](https://pennywise.zynth.dev/guide) walks through every feature step by step, with screenshots.

## Why PennyWise

**Key Differentiators**

- **🔒 Private by Design** — All AI processing and transaction storage happen on your phone; no accounts, no analytics, no servers collecting your data
- **⚡ Zero Setup** — Just grant SMS permission, no accounts to create, works instantly
- **🆓 Free & Open Source** — AGPL v3 licensed, no ads, no trackers; transaction data stays on your device

**Core Features**

- **🤖 Smart SMS Parsing** — <!-- BANKS_SUMMARY -->2 Ghana mobile-money providers<!-- /BANKS_SUMMARY --> with real-time detection of incoming SMS
- **🇬🇭 Ghana Cedi** — GHS is the app's supported currency
- **🏷️ Auto-Categorization & Smart Rules** — Pattern-based rules that auto-categorize transactions
- **💰 Budget Tracking** — Multiple budget groups (Limit/Target/Expected types), daily allowance, category-level budgets
- **📊 Analytics & Charts** — Bar charts, line trends, heatmaps, category breakdowns, top merchants, custom date ranges
- **🏦 Multi-Account & Balance Tracking** — Track multiple bank accounts with live balance from SMS and balance history
- **🔄 Subscription Detection** — Automatic recurring payment detection with due date alerts
- **🎨 Custom Categories** — Create your own with custom colors and icons

**More Features**

- **🔐 Biometric App Lock** — Fingerprint/face unlock with configurable timeout
- **📱 Home Screen Widgets** — Budget progress and recent transactions at a glance
- **📤 Data Export & Backup** — CSV export for taxes, full backup/restore (.pennywisebackup)
- **🎭 Material You Theming** — Dynamic colors, multiple cover styles (Aurora, Gradient, Wave), light/dark/system themes
- **✏️ Manual Transactions** — Add and edit transactions manually
- **🔍 Search & Filters** — Filter by category, merchant, period, currency, transaction type
- **🎯 Guided Onboarding** — Spotlight tutorial for first-time users

### Smart Rules Engine

Rules let you auto-categorize, modify, or block transactions based on configurable **conditions**. A rule consists of one or more "When" conditions and a "Then" action.

**Available condition fields:**

| Field | Description | Example value | Supported operators |
|---|---|---|---|
| `AMOUNT` | Transaction amount | `200` | `<`, `>`, `=` |
| `TYPE` | Income/Expense/Transfer | `INCOME` | `contains`, `equals`, `starts with` |
| `CATEGORY` | Assigned category | `Food & Dining` | `contains`, `equals`, `starts with` |
| `MERCHANT` | Merchant/vendor name | `Amazon` | `contains`, `equals`, `starts with` |
| `NARRATION` | Transaction description | `Monthly rent` | `contains`, `equals`, `starts with` |
| `SMS_TEXT` | Raw SMS body | `OTP` | `contains`, `equals`, `starts with` |
| `BANK_NAME` | Bank name from SMS | `HDFC Bank` | `contains`, `equals`, `starts with` |
| `TIME` / `HOUR` | Transaction time | `09:00` / `9` | `before`, `after`, `at or after`, `at or before` |
| `DAY_OF_WEEK` | Day (1=Mon .. 7=Sun) | `1` | `is`, `is any of`, `is not` |
| `DAY_OF_MONTH` | Day of month (1-31) | `15` | `is`, `is any of`, `before`, `after` |
| `DATE` | Transaction date | `2026-03-21` | `is`, `before`, `after` |
| **`ACCOUNT`** | **Bank account composite** | Select from picker | `is`, `is not` |

**Account condition (ACCOUNT)** — Restricts the rule to transactions from a specific bank account. The value is stored as a `BankName||Last4` composite key (e.g. `HDFC Bank||1234`). When selected, the UI shows an account picker populated from your tracked accounts. Only `EQUALS` and `NOT_EQUALS` operators are available.

Conditions can be combined with **AND** / **OR** logic, and multiple conditions per rule are supported.

**Available actions:**
- **Set** — Override a field (category, merchant, type, description)
- **Append / Prepend** — Add text to a field (merchant, description)
- **Clear** — Remove a field value
- **Block** — Prevent the transaction from being saved

**JSON representation** (stored locally, not exposed via API):
```json
{
  "conditions": [
    {"field": "ACCOUNT", "operator": "EQUALS", "value": "HDFC Bank||1234"},
    {"field": "AMOUNT", "operator": "GREATER_THAN", "value": "500"}
  ],
  "actions": [
    {"field": "CATEGORY", "actionType": "SET", "value": "Food & Dining"}
  ]
}
```

## Supported Banks & Countries

<!-- SUPPORTED_BANKS:START (generated by scripts/update-supported-banks.sh — do not edit by hand) -->
Supporting **2 Ghana mobile-money services** with local SMS parsing:

### 🇬🇭 Ghana — GHS GH₵ (2)
**MTN MoMo**, **Telecel Cash**
<!-- SUPPORTED_BANKS:END -->

Additional Ghana mobile-money templates will be added as the study corpus expands.

## Privacy First

SMS parsing and transaction storage are performed on-device. The app is scoped to Ghanaian cedi and the MTN MoMo and Telecel Cash templates. Its codebase is released under AGPL v3.

### Network access

The Android manifest removes Internet and network-state permissions. Exchange-rate fetching, model download, purchase billing, and SMS report submission have been removed from the app. External links, if opened by the user, are handled by the operating system's browser.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="150"/>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="150"/>
</p>

<details>
<summary>In-app screenshots (light &amp; dark themes)</summary>

<br/>

**Light Theme**

<table>
<tr>
<td><img src="screenshots/home-light.png" width="160"/></td>
<td><img src="screenshots/analytics-light.png" width="160"/></td>
<td><img src="screenshots/analytics-scroll-light.png" width="160"/></td>
<td><img src="screenshots/transaction-detail-light.png" width="160"/></td>
<td><img src="screenshots/chat-light.png" width="160"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Analytics</td>
<td align="center">Categories</td>
<td align="center">Details</td>
<td align="center">AI Chat</td>
</tr>
</table>

**Dark Theme**

<table>
<tr>
<td><img src="screenshots/home-dark.png" width="160"/></td>
<td><img src="screenshots/analytics-dark.png" width="160"/></td>
<td><img src="screenshots/account-detail-dark.png" width="160"/></td>
<td><img src="screenshots/accounts-dark.png" width="160"/></td>
<td><img src="screenshots/chat-dark.png" width="160"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Analytics</td>
<td align="center">Account Detail</td>
<td align="center">Accounts</td>
<td align="center">AI Chat</td>
</tr>
</table>

</details>

## Quick Start

```bash
# Clone repository
git clone https://github.com/sarim2000/pennywiseai-tracker.git
cd pennywiseai-tracker

# Environment check + fast test gate (same compile/test tasks CI runs)
./init.sh

# Build APK
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/standard/debug/app-standard-universal-debug.apk
```

### Requirements

- Android 8.0+ (API 26)
- Android Studio Ladybug or newer
- JDK 21

## Tech Stack

<p align="center">
  <img src="https://skillicons.dev/icons?i=kotlin,androidstudio,materialui" /><br>
  <img src="https://skillicons.dev/icons?i=hilt,room,coroutines" />
</p>

**Architecture**: MVVM • Jetpack Compose • Room • Coroutines • Hilt • Material Design 3

## Community & Support

- **User guide**: Step-by-step walkthroughs, troubleshooting, and search — [pennywise.zynth.dev/guide](https://pennywise.zynth.dev/guide)
- **Discord**: Join the community, share feedback, and get help — [Join Discord](https://discord.gg/H3xWeMWjKQ)
- **Issues**: Report bugs or request features — [Open an issue](https://github.com/sarim2000/pennywiseai-tracker/issues)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before participating.

```bash
./gradlew test          # Run tests
./gradlew lint   # Check style
```

## Security

Please review our [Security Policy](SECURITY.md) for how to report vulnerabilities.

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Lucifer1590"><img src="https://avatars.githubusercontent.com/Lucifer1590?v=4&s=100" width="100px;" alt="Lucifer1590"/><br /><sub><b>Lucifer1590</b></sub></a><br /><a href="#community-Lucifer1590" title="Community Management">👥</a> <a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3ALucifer1590" title="Bug reports">🐛</a> <a href="#userTesting-Lucifer1590" title="User Testing">📓</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/akshaynexus"><img src="https://avatars.githubusercontent.com/akshaynexus?v=4&s=100" width="100px;" alt="akshaynexus"/><br /><sub><b>akshaynexus</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=akshaynexus" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/WINDY-WINDWARD"><img src="https://avatars.githubusercontent.com/WINDY-WINDWARD?v=4&s=100" width="100px;" alt="WINDY-WINDWARD"/><br /><sub><b>WINDY-WINDWARD</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=WINDY-WINDWARD" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/brukberhane"><img src="https://avatars.githubusercontent.com/brukberhane?v=4&s=100" width="100px;" alt="brukberhane"/><br /><sub><b>brukberhane</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=brukberhane" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/KaushalJyanni"><img src="https://avatars.githubusercontent.com/KaushalJyanni?v=4&s=100" width="100px;" alt="KaushalJyanni"/><br /><sub><b>KaushalJyanni</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=KaushalJyanni" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/cmazwar"><img src="https://avatars.githubusercontent.com/cmazwar?v=4&s=100" width="100px;" alt="cmazwar"/><br /><sub><b>cmazwar</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=cmazwar" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/AyonPal"><img src="https://avatars.githubusercontent.com/AyonPal?v=4&s=100" width="100px;" alt="AyonPal"/><br /><sub><b>AyonPal</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=AyonPal" title="Code">💻</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/rafi-ghanbari"><img src="https://avatars.githubusercontent.com/rafi-ghanbari?v=4&s=100" width="100px;" alt="rafi-ghanbari"/><br /><sub><b>rafi-ghanbari</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=rafi-ghanbari" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Chevindu"><img src="https://avatars.githubusercontent.com/Chevindu?v=4&s=100" width="100px;" alt="Chevindu"/><br /><sub><b>Chevindu</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=Chevindu" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/arunpdl"><img src="https://avatars.githubusercontent.com/arunpdl?v=4&s=100" width="100px;" alt="arunpdl"/><br /><sub><b>arunpdl</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=arunpdl" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/hegdenischay"><img src="https://avatars.githubusercontent.com/hegdenischay?v=4&s=100" width="100px;" alt="hegdenischay"/><br /><sub><b>hegdenischay</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=hegdenischay" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/shreyes-shalgar"><img src="https://avatars.githubusercontent.com/shreyes-shalgar?v=4&s=100" width="100px;" alt="shreyes-shalgar"/><br /><sub><b>shreyes-shalgar</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Ashreyes-shalgar" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/test2a"><img src="https://avatars.githubusercontent.com/test2a?v=4&s=100" width="100px;" alt="test2a"/><br /><sub><b>test2a</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Atest2a" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/AlphaXyph"><img src="https://avatars.githubusercontent.com/AlphaXyph?v=4&s=100" width="100px;" alt="AlphaXyph"/><br /><sub><b>AlphaXyph</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AAlphaXyph" title="Bug reports">🐛</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/kamranbahrami261-beep"><img src="https://avatars.githubusercontent.com/kamranbahrami261-beep?v=4&s=100" width="100px;" alt="kamranbahrami261-beep"/><br /><sub><b>kamranbahrami261-beep</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Akamranbahrami261-beep" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jerryn70"><img src="https://avatars.githubusercontent.com/jerryn70?v=4&s=100" width="100px;" alt="jerryn70"/><br /><sub><b>jerryn70</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Ajerryn70" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/albert-godinho"><img src="https://avatars.githubusercontent.com/albert-godinho?v=4&s=100" width="100px;" alt="albert-godinho"/><br /><sub><b>albert-godinho</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Aalbert-godinho" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Mikcharls311"><img src="https://avatars.githubusercontent.com/Mikcharls311?v=4&s=100" width="100px;" alt="Mikcharls311"/><br /><sub><b>Mikcharls311</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AMikcharls311" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/rhsjudb"><img src="https://avatars.githubusercontent.com/rhsjudb?v=4&s=100" width="100px;" alt="rhsjudb"/><br /><sub><b>rhsjudb</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3Arhsjudb" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/xRahul"><img src="https://avatars.githubusercontent.com/xRahul?v=4&s=100" width="100px;" alt="xRahul"/><br /><sub><b>xRahul</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=xRahul" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/haithamassoli44"><img src="https://avatars.githubusercontent.com/haithamassoli44?v=4&s=100" width="100px;" alt="haithamassoli44"/><br /><sub><b>haithamassoli44</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=haithamassoli44" title="Code">💻</a> <a href="#translation-haithamassoli44" title="Translation">🌍</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/khushalmajoka"><img src="https://avatars.githubusercontent.com/khushalmajoka?v=4&s=100" width="100px;" alt="khushalmajoka"/><br /><sub><b>khushalmajoka</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=khushalmajoka" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/paul-tharun"><img src="https://avatars.githubusercontent.com/paul-tharun?v=4&s=100" width="100px;" alt="paul-tharun"/><br /><sub><b>paul-tharun</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=paul-tharun" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/chikaezeani"><img src="https://avatars.githubusercontent.com/chikaezeani?v=4&s=100" width="100px;" alt="chikaezeani"/><br /><sub><b>chikaezeani</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=chikaezeani" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/8valet"><img src="https://avatars.githubusercontent.com/8valet?v=4&s=100" width="100px;" alt="8valet"/><br /><sub><b>8valet</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=8valet" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/tashifkhan"><img src="https://avatars.githubusercontent.com/tashifkhan?v=4&s=100" width="100px;" alt="tashifkhan"/><br /><sub><b>tashifkhan</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=tashifkhan" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/gingerjoos"><img src="https://avatars.githubusercontent.com/gingerjoos?v=4&s=100" width="100px;" alt="gingerjoos"/><br /><sub><b>gingerjoos</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=gingerjoos" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/dhruvitdiyora"><img src="https://avatars.githubusercontent.com/dhruvitdiyora?v=4&s=100" width="100px;" alt="dhruvitdiyora"/><br /><sub><b>dhruvitdiyora</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=dhruvitdiyora" title="Code">💻</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Vijayabaskar56"><img src="https://avatars.githubusercontent.com/Vijayabaskar56?v=4&s=100" width="100px;" alt="Vijayabaskar56"/><br /><sub><b>Vijayabaskar56</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=Vijayabaskar56" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Reputable2772"><img src="https://avatars.githubusercontent.com/Reputable2772?v=4&s=100" width="100px;" alt="Reputable2772"/><br /><sub><b>Reputable2772</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=Reputable2772" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/IndrajeetGhatge"><img src="https://avatars.githubusercontent.com/IndrajeetGhatge?v=4&s=100" width="100px;" alt="IndrajeetGhatge"/><br /><sub><b>IndrajeetGhatge</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=IndrajeetGhatge" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/FaintFlower"><img src="https://avatars.githubusercontent.com/FaintFlower?v=4&s=100" width="100px;" alt="FaintFlower"/><br /><sub><b>FaintFlower</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/commits?author=FaintFlower" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/Ash-jib-home"><img src="https://avatars.githubusercontent.com/Ash-jib-home?v=4&s=100" width="100px;" alt="Ash-jib-home"/><br /><sub><b>Ash-jib-home</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AAsh-jib-home" title="Bug reports">🐛</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/EzraTD12"><img src="https://avatars.githubusercontent.com/EzraTD12?v=4&s=100" width="100px;" alt="EzraTD12"/><br /><sub><b>EzraTD12</b></sub></a><br /><a href="https://github.com/sarim2000/pennywiseai-tracker/issues?q=author%3AEzraTD12" title="Bug reports">🐛</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!

## Star History

[![Star History Chart](https://star-history.dera.page/svg?repos=sarim2000/pennywiseai-tracker&type=Date)](https://star-history.dera.page/#sarim2000/pennywiseai-tracker&type=date)

## License

GNU Affero General Public License v3.0 - see [LICENSE](LICENSE)

This project is licensed under AGPL v3, which means:
- ✅ You can use, modify, and distribute this software
- ✅ You must share your modifications under the same license
- ✅ If you run a modified version on a server, you must make the source code available to users
- ✅ Patent rights are explicitly granted and protected

---

<p align="center">
<a href="https://github.com/sarim2000/pennywiseai-tracker/releases">Download</a> •
<a href="https://apps.apple.com/app/id6793073167">iOS App</a> •
<a href="https://github.com/sarim2000/pennywiseai-tracker/issues">Report Bug</a> •
<a href="https://github.com/sarim2000/pennywiseai-tracker/issues">Request Feature</a>
</p>
