# DumberKit: Contacts
<img alt="Logo" src="graphics/icon.png" width="120" />

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://ko-fi.com/lbockaj)

Most contact apps assume you have a touchscreen. On a keypad phone like the Qin F21, Dumber Mini or Doov R77 Pro
that means fiddly floating buttons, things you can't reach with the D-pad and screens that don't fit. DumberKit:
Contacts is built on [Fossify Contacts](https://github.com/FossifyOrg/Contacts) and reworked so that every screen,
menu and dialog works with the keys alone, on a screen as small as 480×640.

You move with the arrows, open things with OK, hold OK for more options, and the green Call key calls whoever is
selected. Touch is switched off inside the app, so nothing gets pressed by accident in your pocket.

<div align="center">
<img alt="Contact list" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="19%">
<img alt="Contact details" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="19%">
<img alt="Jump to letter" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="19%">
<img alt="Search" src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="19%">
<img alt="Favorites" src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="19%">
</div>

## Using it with the keys

The same few keys do the same things everywhere: **OK** opens or picks, **holding OK** shows what you can do with
the selected item, **Menu** shows what you can do on the current screen and **Back** goes back.

### Contact list

The main screen has three tabs – Contacts, Favorites and Groups – and opens on the one you used last. The focus
starts on the **Ab** button in the top bar, one press of ↓ takes you to the first contact.

| Key | What it does |
| --- | --- |
| **↑ / ↓** | Move through the contacts. ↑ on the first one goes to the top bar: *Ab*, *Search* and *⋮ Menu* |
| **← / →** | Switch between Contacts, Favorites and Groups; each tab starts on its first item |
| **OK** | Open the contact |
| **Hold OK** | Send SMS, add to or remove from favorites, edit, share, delete |
| **Call** (green key) | Call the selected contact |
| **#** | Send an SMS to the selected contact |
| **\*** | Jump to a letter (same as the *Ab* button) |
| **Menu** | New contact, sort, filter, dialer, settings, about |
| **Back** | Back to the first contact; pressing it there leaves the app |

### Finding someone

- **Jump to a letter** – *Ab* or **\*** opens a small A–Z grid. Letters nobody's name starts with are greyed out
  and skipped. Pick a letter with the arrows and OK, or press its digit key like when texting: `2` goes to A,
  pressing `2` again to B, then C. The list jumps to the first contact starting with that letter. **Back** closes
  the grid.
- **Search** – the magnifier in the top bar (or the phone's Search key) opens the search field with the keyboard
  ready. With [Traditional T9](https://github.com/sspanak/tt9) it starts in letter (ABC) mode, so you can type a
  name right away. Names that start with what you typed come first. **OK** or **↓** moves to the results, **Back**
  closes the search. Typing on the list itself doesn't start a search, so a stray key press does nothing.

### Contact details

The phone numbers come first, followed by *Send SMS*, *Add to favorites*, *Edit contact*, *Share* and *Delete* as
rows you can reach with the arrows. Messenger accounts (WhatsApp, Viber, Signal, Telegram…) are listed with their
icons and the contact's storage is shown at the bottom.

| Key | What it does |
| --- | --- |
| **↑ / ↓** | Move through the numbers and the actions |
| **OK** | Call the selected number, or run the selected action |
| **Call** | Call the selected number |
| **#** | Send an SMS to the selected number |
| **Menu** | Edit, share, delete, open with another app, choose which fields are shown |

### Editing a contact

The form is walked through in reading order: ↓ goes field by field from top to bottom, and within a row from left
to right (number → default star → number type → next number). At the end, under a divider, are *Add field*,
the storage, and the **Cancel** (red cross) and **Save** (green check) rows.

| Key | What it does |
| --- | --- |
| **↑ / ↓** | Previous / next field |
| **Add field** row | Add a phone number, e-mail, address, birthday and more |
| **Menu** | Add field, favorite, photo, share, delete, shown fields, save |
| **Back** | Asks whether to save the changes – *No* is preselected, so a quick OK doesn't save by mistake |

**Dates** (birthdays, anniversaries) are picked in three columns: **← / →** move through day, month, year,
*Hide year*, *Cancel* and *OK*, **↑ / ↓** change the value, and the digit keys type it directly – `1` `5` for the
15th, `3` for March, `1` `9` `8` `0` for the year.

### Favorites and groups

| Where | Key | What it does |
| --- | --- | --- |
| Favorites | **Hold OK** | The contact actions, with *Remove from favorites* instead of *Delete* |
| Groups tab | **OK** | Open the group |
| Groups tab | **Hold OK** | Rename or delete the group |
| Group | **Menu** | Add contacts, SMS to the group, e-mail to the group, ringtone |
| Group | **Hold OK** | The contact actions, with *Remove from group* instead of *Delete* |
| Group | **Call** / **#** | Call or text the selected member |

An empty tab or group shows a single round **+** button: in Favorites it adds favorites, in Groups it creates a
group and in an empty group it adds members.

**Picking contacts** (for favorites or a group) is a list with a checkbox on each row: **OK** ticks a contact,
**→** jumps to the green check (save) or the red cross (cancel) on the right, **←** goes back to the list.

### Settings and colors

Every option in *Settings* is a row you can reach with the arrows. Screens where changes need confirming, like
the colors, end with **Cancel** / **Save** rows instead of icons in the top bar.

- **Colors** – *System* (follows Android), light, dark and other themes, or your own colors. The color picker
  works in steps: the hue bar on the left, OK, the shade square, OK, then *Cancel* or *Save*. **Back** steps back.
- **App icon** – red by default, any other color can be picked; switching the theme doesn't change it.
- **Allow touch input** – off by default. Turn it on if you'd like to use the touchscreen in the app too. Other apps
  opened from here (the file picker, the dialer) always keep touch.
- **Shown fields** – the phone numbers are shown under the names in the list; that and the other fields can be
  switched off in *Manage shown contact fields*.

<div align="center">
<img alt="Picking contacts" src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="19%">
<img alt="Group" src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="19%">
<img alt="Group menu" src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="19%">
<img alt="Editing a contact" src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width="19%">
<img alt="About" src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="19%">
</div>

## What else it does

- Numbers are formatted for your country and the default number is marked with a small star.
- Import and export vCard (.vcf) files through Android's own file picker, plus automatic backups.
- No ads, no tracking and no internet access. Your contacts stay in Android's own contact storage – on the phone,
  the SIM or your sync account – and other apps keep seeing them as usual.
- Portrait only, sized for small screens, and translated into over 40 languages.

## Install

1. Download `dumberkit-contacts.apk` from the [latest release](../../releases/latest).
2. Open it on the phone and allow installing apps from that source when Android asks.
3. Start **Contacts** with the red icon and allow access to your contacts.

Requires Android 8.0 or newer.

## Build

```bash
./gradlew assembleFossRelease
```

The release build is signed with the key from `keystore.properties` (see `keystore.properties_sample`), the APK
ends up in `app/build/outputs/apk/foss/release/`. Debug builds are noticeably slower on low-end keypad phones,
judge the speed on a release build.

## License

DumberKit: Contacts is free software under the [GNU General Public License v3.0](LICENSE).

## Credits

The core of the app is [Fossify Contacts](https://github.com/FossifyOrg/Contacts) and its
[Commons](https://github.com/FossifyOrg/Commons) library (GPL-3.0) – contact storage, editing, import/export, themes
and translations come from there. DumberKit: Contacts is a modified version, reworked in 2026 for keypad navigation
and small screens. Thanks to the Fossify team and its translators.
