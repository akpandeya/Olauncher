# Theme pack format

A theme pack is one JSON file. It sets the colours, the font and the bold flag of the launcher.
Packs are bundled with the app and can be imported from a JSON file; export writes this same
format.

**Wallpaper and text size are not part of a pack.** Those settings stay where they are when a pack
is applied. Nothing in this file may set or override them, and the pack picker must not offer them.

## File location and naming

- Bundled packs live in `docs/design/themes/packs/` as `<name>-<mode>.json` (base) and
  `<name>-bold-<mode>.json` (bold version), all lowercase, for example `thumbline-light.json` and
  `thumbline-bold-light.json`.
- Every pack has a **bold version**: a separate pack with the same colours and `"bold": true`, so it
  can be picked from the list in one step (for example "Diwali Bold"). A pack whose base already
  ships bold (Halloween, Diwali, Christmas) has a Bold version with identical colours.

## Fields

| field | type | format | required | meaning |
|---|---|---|---|---|
| `name` | string | non-empty, unique among bundled packs | yes | Name shown in the picker, for example `Thumbline Bold` |
| `mode` | string | exactly `light` or `dark` | yes | Which variant this file is |
| `background` | string | `#RRGGBB` hex | yes | Home background colour |
| `surface` | string | `#RRGGBB` hex | yes | Cards, sheets, dialogs |
| `ink` | string | `#RRGGBB` hex | yes | Primary text and icons |
| `inkMuted` | string | `#RRGGBB` hex | yes | Secondary text (dates, hints) |
| `accent` | string | `#RRGGBB` hex | yes | Thumbline accent, pills, highlights |
| `accentSecondary` | string | `#RRGGBB` hex | yes | Second accent (weather, charts) |
| `snow` | string | `#RRGGBB` hex | no | Snow colour; only Christmas packs set it |
| `font` | string | one of `Inter`, `IBM Plex Sans`, `Nunito` | yes | Font family; must be a bundled font |
| `bold` | boolean | `true` / `false` | yes | Bold text on or off |

Hex values are uppercase or lowercase `#RRGGBB`, six digits, no alpha, no shorthand. `mode` has no
other values — `system`, `auto` or `Light` are rejected. There are no extra fields: unknown fields
are rejected so a typo cannot silently do nothing.

## Validation (rejection rules)

An import or a bundled file is rejected when any of these is true:

1. **Bad hex** — any colour field that is not `#RRGGBB` (wrong length, non-hex characters, `rgb()`,
   named colours).
2. **Missing required field** — any field in the table marked required, including `bold` (`false`
   must be explicit, not implied by absence).
3. **Unknown mode** — `mode` is not exactly `light` or `dark`.
4. **Failed AA contrast** — the variant does not pass the thresholds in
   [`CONTRAST.md`](CONTRAST.md): 4.5:1 for `ink` and `inkMuted` text against `background` and
   `surface`; and for `accent` and `accentSecondary`, either 3.0:1 against `background` (accent-only
   use) **or** at least 4.5:1 with a text colour on the accent (fill use, dark `#0B0B0C` or white).
   The error names the pair and the measured ratio.

A rejected import changes nothing: the current pack stays active.

## Worked example

`thumbline-light.json` (Thumbline, light variant):

```json
{
  "name": "Thumbline",
  "mode": "light",
  "background": "#FFFFFF",
  "surface": "#F1F1EF",
  "ink": "#0E0E0F",
  "inkMuted": "#5A5A60",
  "accent": "#D9F24B",
  "accentSecondary": "#C2185B",
  "font": "Inter",
  "bold": false
}
```

Every field from the table appears, colours are `#RRGGBB`, `mode` is a bare `light`/`dark`, and
`bold` is written out even though it is `false`. A Christmas pack would add one optional field:

```json
"snow": "#6FB594"
```

The Bold version of the same pack, `thumbline-bold-light.json`, is identical except:

```json
"name": "Thumbline Bold",
"bold": true
```

## Decorative dressing (future idea, not in this format)

The mocks explore seasonal decoration keyed to a pack family — Halloween (web, bats, ghost,
jack-o'-lantern), Diwali (diyas, fireworks, lotus) and Christmas (string lights, Santa hat, tree,
gifts, `snow`). This is intentionally **not** pack data: the JSON above stays colours + font +
bold, decoration never carries meaning the user configures, and non-seasonal packs (Thumbline,
Sage, Midnight, Rose) stay flat. If the app later wants it, it should be an app-side presentation
choice derived from the pack name or an additive optional field in a format version bump — not a
required field, and never something the importer rejects over.
