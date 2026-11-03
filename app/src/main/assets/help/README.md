# AeroCam help content

The app supports English (`en`), German (`de`), Spanish (`es`), French (`fr`), Italian (`it`) and Portuguese (`pt`). Every language contains all five active Help topics and the camera setup guide:

- `get-ready-for-a-ride.md`
- `prevent-standby.md`
- `auto-highlights.md`
- `different-widgets.md`
- `gps-telemetry.md`
- `recommended-camera-setup.md`

Keep filenames identical across language folders. `HelpContent.load` chooses the device language (including regional variants such as `pt-BR` → `pt`) and retains English as a fallback for unsupported languages or a missing file. Older filenames remain legacy assets and are not in the Help index.

Supported Markdown: headings (`#`, `##`, `###`), bulleted/numbered lists, bold text and callouts. The ride guide uses the local `dji-lock-menu` illustration token; its caption and accessibility description come from the localized `lock_menu_caption` string. Keep the token unchanged when translating.

Use the actual translated app labels in navigation instructions. The English DJI menu names are retained as identifiers because camera menus differ by language and firmware. Keep paragraphs short for the Karoo display. Prevent-standby Help describes user-facing behavior without explaining internal camera commands.

Uphill and downhill are independent, opt-in triggers. Each requires its own speed and signed gradient thresholds to be met together. Use positive percentages for uphill and negative percentages for downhill. The existing downhill defaults remain off / 50 km/h / −3%; uphill starts off / 20 km/h / +3%.

The localization tests check active-file coverage, language resolution, localized image descriptions and matching UI string keys/format arguments across every supported language.
