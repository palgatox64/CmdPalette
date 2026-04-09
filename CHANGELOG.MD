# Changelog

All notable changes to this project are documented in this file.

## 1.0.0 - 2026-04-09

First public functional release of CmdPalette.

### Added
- Command palette with quick open and direct execution.
- Navigation tabs for history and categories.
- Favorites category integrated into the category system.
- Custom category management (create, select, delete, and remove commands by row).
- Persistent executed-command history.
- Executed history browsing from the input using Up/Down arrows.
- Complete theme system with base presets and custom themes.
- Per-aspect color editing (HEX + RGB controls).
- Rename and delete actions for editable themes.
- Theme file workflow for external editing with automatic reload.
- Configurable keybindings from Minecraft Controls with a dedicated mod category.
- Default shortcuts: open with Ctrl+Enter, close with Esc or Ctrl+X.
- Data scope configuration:
	- Global
	- Per server
	- Per world (per save in singleplayer)

### UX/UI Improvements
- Persistent settings button in the top bar.
- Restored and separated contextual action button (clear history / delete category).
- Quick action buttons (+ and favorite) are hidden when the input is empty.
- Input bar is hidden while in Settings view.
- Fixed theme action button widths for localized labels.
- Fixed ON/OFF switch width alignment with the controls column.
- Dynamic width fix for the mode button (Global/Per server/Per world).

### Persistence and Data
- Settings migration to extended format with scopeMode.
- Category and history data separated by selected scope.
- Per-world persistence based on the actual save folder (level.dat), preventing cross-save mixing.
- Sanitized profile keys for safe storage paths.

### Internationalization
- Full and adjusted Spanish/English labels for actions and settings.
- New language keys for data scope modes and keybind category.

### Compatibility
- Backward-compatible settings behavior: if scopeMode is missing, Global is used.
