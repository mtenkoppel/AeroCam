# AeroCam help content

Help articles are Markdown files grouped by language code.

```text
help/
├── en/
│   ├── how-to-use.md
│   ├── keep-awake.md
│   ├── auto-highlights.md
│   └── tips-and-tricks.md
└── de/
    └── ...same filenames...
```

To add a translation, copy the `en` directory, rename it to the language code, and translate the contents. Keep the filenames unchanged. AeroCam uses English when a translated file is missing.

Supported formatting:

- `#`, `##` and `###` headings
- Bulleted and numbered lists
- `**bold text**`
- `> **Label:** Callout text`

Keep paragraphs short for the Karoo display. Images can later be referenced from a language folder without changing the article structure, once image rendering is added to the Help view.

Use these terms consistently:

- **Keep awake**: periodic QS commands that prevent automatic power-off
- **Auto highlights**: automatic highlight creation during a ride
- **Trigger**: the ride event that requests a highlight
- **Highlight**: a marker inside a video recording
- **Auto-start recording**: start recording when a trigger occurs while idle
- **Auto-reconnect**: reconnect to the already paired camera when it is turned on
- **Data field**: an AeroCam control placed on a Karoo ride page
- **Put camera to sleep**: fully power off the camera; AeroCam cannot reverse this
