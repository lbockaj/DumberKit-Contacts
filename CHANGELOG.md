# Changelog

## [1.0.0] - 2026-10-09
First public release.
### Changed
- The language list offers only languages with a real translation (over 40); the others showed up in English anyway
- Smaller app: 4.4 MB
- README with a full guide to the keys

## [0.8.4] - 2026-10-09
### Fixed
- Picking a theme (light, dark, white…) no longer turns the app icon green, the icon keeps its color; the theme is still recognized afterwards instead of showing as Custom

## [0.8.3] - 2026-10-09
### Changed
- Smaller app (APK 6.4 → 5.0 MB): Commons' own About, license, FAQ, contributors, donation and blocked numbers screens, never opened here, are left out

## [0.8.2] - 2026-10-09
### Fixed
- Switching tabs no longer jumps around: the list is already at the top when shown and the focus doesn't flash on the top bar

## [0.8.1] - 2026-10-09
### Changed
- Phone numbers are shown under the names by default on a fresh install
### Fixed
- Switching back to a tab with ← / → put the focus where the list was left, now it starts on the first contact

## [0.8.0] - 2026-10-04
### Added
- Groups work with the keypad: a group's screen has no floating button, Menu offers Add contacts, Send SMS / e-mail to the group and Ringtone, Call and # work on its contacts, holding OK offers Remove from group
- Holding OK on a group in the Groups tab offers Rename and Delete; the focus lands on a new group, or back in the list after deleting
- Picking contacts (for a group or favorites): one stop per row, no touch-only letter bar
- Up/down leave the text field in dialogs (new group, rename, custom label, export, backups)
### Changed
- New launcher icon (address book), color still selectable
- Touch blocking simplified: the app's windows simply don't take touches while touch is off
### Fixed
- On Android 9 the letter grid opened with * didn't react to keys
- A group created from the empty Groups tab didn't show up until restart, when the search had been used before

## [0.7.0] - 2026-10-04
### Added
- Touch is ignored in the whole app (screens, dialogs, menus), keys only; Settings → "Allow touch input" turns it back on. Other apps opened from here (file picker, dialer) keep touch
### Changed
- Import and export use Android's own file picker again, no "All files access" permission
### Fixed
- Crash on the first start of a fresh install, before the contacts permission was granted
- Removed Commons' color-sync permission with the Fossify name

## [0.6.0] - 2026-10-04
### Added
- The target contact source in the import dialog can be reached and changed with the keys
### Changed
- Search opens only with the magnifier button and connects the T9 keyboard right away; typing in the contact list no longer starts a search
- Top bar: Jump to letter (Ab), Search, Menu; New contact / Add favorites / Create group moved to the top of the menu
- The app starts with the focus on Ab
- Opening Settings or the dialer from the menu no longer flashes the focus

## [0.5.0] - 2026-10-04
### Added
- Jump to a letter: the "Ab" button next to search, or the * key, opens an A–Z grid; letters without contacts are greyed out, keys 2–9 pick letters like on the keypad
### Fixed
- Back to the first contact no longer flashes the "+" button or jumps back to the old row
- Cancel / Save rows on the color screen stay readable after picking another theme there

## [0.4.0] - 2026-10-04
### Changed
- New name and package: DumberKit Contacts (`com.dumberkit.contacts`), installs as a new app
- Own About screen with the keypad shortcuts
- Color picker in steps: hue bar, shade square, then Cancel / Save rows
- Roomier settings list, System theme and red icon by default
- Back in the contact list jumps to the first contact before leaving the app

## [0.3] - 2026-10-04
### Changed
- Release build: the contact list opens about four times faster than in the debug build

## [0.2] - 2026-10-03
### Added
- Full keypad navigation: contact list, details, editor, dialogs and date picker
- T9 search, Call and # shortcuts, contact actions on a long OK press

## [0.1] - 2026-10-03
### Added
- First keypad version of the main screen
