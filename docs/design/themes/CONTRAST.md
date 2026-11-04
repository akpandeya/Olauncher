# Theme pack contrast report

One row per variant (28 rows: 14 base + 14 bold). Bold versions share colours with their base
variant, so their ratios are identical; they are listed separately because the picker treats them
as separate packs.

## Formula

WCAG 2.x relative luminance:

```
for each sRGB channel c in [0,1]:
    c_lin = c / 12.92                    if c <= 0.03928
    c_lin = ((c + 0.055) / 1.055) ^ 2.4  otherwise
L = 0.2126 * R_lin + 0.7152 * G_lin + 0.0722 * B_lin
ratio = (L_lighter + 0.05) / (L_darker + 0.05)
```

## Thresholds

- **4.5:1** — body text (`ink`, `inkMuted`).
- **3.0:1** — large text and accent-only uses (icons, small highlights): `accent`, `accentSecondary` against `background`.

## Results

| variant | ink / background | ink / surface | inkMuted / surface | accent / background | accentSecondary / background |
|---|---|---|---|---|---|
| Thumbline | 19.67 pass | 17.38 pass | 7.43 pass | 15.69 pass | 7.35 pass |
| Thumbline Bold | 19.67 pass | 17.38 pass | 7.43 pass | 15.69 pass | 7.35 pass |
| Thumbline (light) | 19.29 pass | 17.06 pass | 6.06 pass | 1.25 **FAIL** | 5.87 pass |
| Thumbline Bold (light) | 19.29 pass | 17.06 pass | 6.06 pass | 1.25 **FAIL** | 5.87 pass |
| Sage | 15.30 pass | 13.46 pass | 6.61 pass | 7.53 pass | 7.23 pass |
| Sage Bold | 15.30 pass | 13.46 pass | 6.61 pass | 7.53 pass | 7.23 pass |
| Sage (light) | 14.47 pass | 12.84 pass | 5.34 pass | 5.76 pass | 5.69 pass |
| Sage Bold (light) | 14.47 pass | 12.84 pass | 5.34 pass | 5.76 pass | 5.69 pass |
| Midnight | 16.15 pass | 14.05 pass | 7.24 pass | 9.76 pass | 7.94 pass |
| Midnight Bold | 16.15 pass | 14.05 pass | 7.24 pass | 9.76 pass | 7.94 pass |
| Midnight (light) | 16.14 pass | 14.18 pass | 5.59 pass | 5.11 pass | 5.70 pass |
| Midnight Bold (light) | 16.14 pass | 14.18 pass | 5.59 pass | 5.11 pass | 5.70 pass |
| Rose | 15.77 pass | 13.80 pass | 7.63 pass | 7.52 pass | 10.45 pass |
| Rose Bold | 15.77 pass | 13.80 pass | 7.63 pass | 7.52 pass | 10.45 pass |
| Rose (light) | 16.29 pass | 14.34 pass | 6.15 pass | 6.05 pass | 5.34 pass |
| Rose Bold (light) | 16.29 pass | 14.34 pass | 6.15 pass | 6.05 pass | 5.34 pass |
| Halloween | 16.02 pass | 14.55 pass | 7.72 pass | 7.37 pass | 7.28 pass |
| Halloween Bold | 16.02 pass | 14.55 pass | 7.72 pass | 7.37 pass | 7.28 pass |
| Halloween (light) | 13.79 pass | 15.41 pass | 6.50 pass | 2.03 **FAIL** | 5.98 pass |
| Halloween Bold (light) | 13.79 pass | 15.41 pass | 6.50 pass | 2.03 **FAIL** | 5.98 pass |
| Diwali | 15.88 pass | 14.01 pass | 8.21 pass | 9.06 pass | 5.89 pass |
| Diwali Bold | 15.88 pass | 14.01 pass | 8.21 pass | 9.06 pass | 5.89 pass |
| Diwali (light) | 12.83 pass | 13.55 pass | 7.05 pass | 2.08 **FAIL** | 5.57 pass |
| Diwali Bold (light) | 12.83 pass | 13.55 pass | 7.05 pass | 2.08 **FAIL** | 5.57 pass |
| Christmas | 14.78 pass | 12.14 pass | 7.34 pass | 5.74 pass | 11.00 pass |
| Christmas Bold | 14.78 pass | 12.14 pass | 7.34 pass | 5.74 pass | 11.00 pass |
| Christmas (light) | 11.25 pass | 11.72 pass | 5.64 pass | 5.34 pass | 5.11 pass |
| Christmas Bold (light) | 11.25 pass | 11.72 pass | 5.64 pass | 5.34 pass | 5.11 pass |

## Accent used as a pill fill

Accent appears two ways in the app: as an **accent-only** element (icon, small highlight, judged
against `background` above) and as a **pill fill** with text on top (for example the next-event
chip). Text on a fill is judged against `accent`, not `background`. The owner decision for this
epic: the Thumbline accent stays `#D9F24B`, and lower-contrast accent fills in the light variants
(accent as a pill background with dark text) are accepted.

Text colour on every accent fill in the palettes:

| variant | accent | text colour on the fill | ratio |
|---|---|---|---|
| Thumbline (dark) | `#D9F24B` | `#0B0B0C` | 15.69 |
| Thumbline (light) | `#D9F24B` | `#0B0B0C` | 15.69 |
| Sage (dark) | `#9CAF88` | `#0B0B0C` | 8.34 |
| Sage (light) | `#4D6653` | `#FFFFFF` | 6.29 |
| Midnight (dark) | `#5CC8FF` | `#0B0B0C` | 10.46 |
| Midnight (light) | `#0B6FA8` | `#FFFFFF` | 5.45 |
| Rose (dark) | `#FF7AA8` | `#0B0B0C` | 8.06 |
| Rose (light) | `#B8174F` | `#FFFFFF` | 6.42 |
| Halloween (dark) | `#FF7A1A` | `#0B0B0C` | 7.54 |
| Halloween (light) | `#F08A24` | `#0B0B0C` | 7.84 |
| Diwali (dark) | `#FFA21F` | `#0B0B0C` | 9.78 |
| Diwali (light) | `#F29A1F` | `#0B0B0C` | 8.82 |
| Christmas (dark) | `#FF6459` | `#0B0B0C` | 6.76 |
| Christmas (light) | `#C62A2F` | `#FFFFFF` | 5.57 |

## Failures and proposals

Every `ink`, `inkMuted` and `accentSecondary` row passes its threshold. The only below-threshold
cells against `background` are the three light-variant accents listed below, all of which the owner
accepted for fill use. The AA hexes are recorded in case a future epic wants light-variant accent
to also pass as an accent-only colour.

| variant | pair | ratio | verdict | AA-proposed accent | owner decision |
|---|---|---|---|---|---|
| Thumbline (light) | accent / background | 1.25 | FAIL — fill use accepted by owner | `#8D9D31` | keep `#D9F24B` for fills |
| Halloween (light) | accent / background | 2.03 | FAIL — fill use accepted by owner | `#C3701D` | keep `#F08A24` for fills |
| Diwali (light) | accent / background | 2.08 | FAIL — fill use accepted by owner | `#C87F1A` | keep `#F29A1F` for fills |
