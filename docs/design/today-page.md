# Today page: Bento layout (option C)

Mock-ups: `mockups/OptionC2.dc.html` (Today view), `mockups/OptionC7.dc.html` (Wellbeing 7 days view).

A scroll of rounded cards on a 2-column grid. Full-width cards span both columns. Words and Notes sit side by side as half-width cards. Dark surface, small grey labels, large numbers.

Each card below lists what it shows, its empty state and its data source (existing code in `app/src/main/java/app/olauncher/ui/today/cards/`).

## Greeting
- Weekday as a large title, date under it ("Samstag", "24. Oktober").
- Empty state: none, always shown.
- Data: system date.

## Intention line
- Label "Today I want to…" (default prefix), the user's text below it, and a "Past" link on the right.
- Empty state: label plus a hint to tap and write one line.
- Data: the intention prefs (same source as today's intention line).

## Weather
- Large weather icon, temperature (e.g. 11°), a line with the condition and high/low ("Overcast · 12° / 7°").
- Right side: "Dry for 12 h" and the place and update time.
- Six-hour strip below: for each of the next six hours, the hour, an icon and the temperature.
- Empty state: no location or no forecast yet: one line saying weather is not available, with the action to set it up.
- Data: `WeatherCard.kt`, `Forecast` and `HeroFormatting` in `app.olauncher.glance`.

## Words (half width)
- Label "Words · DE". Due count with "due", and a line "10 words · 3 learned".
- All-done state: due is 0 and there are words: show "0 due" with the words line (or a tick), tap opens the Words page.
- Empty state: no words yet: "0", "no words", tap opens the Words page.
- Data: `WordsCard.kt`, `WordsStore.stats(today)` (`WordStats(total, due, learned)`). Shown only when German is on.

## Notes (half width)
- Label "Notes · 4" (open count), the latest note, and "+ Add note".
- Empty state: "No notes yet" with "+ Add note".
- Data: `NotesCard.kt`, `Notes` / `NotesStore` (`NOTES_JSON`).

## Wellbeing (full width)
Header: label "Wellbeing" and a segmented toggle "Today" / "7 days".

**Today**
- Distracting screen time as a large figure ("4 h 7 min"), then a line with opens and top app ("Distracting · 107 opens · top: YouTube 4 h 7 min").
- Three tiles: Water, Walk, Not now (counts for today).
- A line "Opened anyway 18 · mostly 'Look something up'".

**7 days** (`OptionC7`)
- Total distracting time for the week and the change against the week before ("20% less than the week before").
- Seven-day bar strip, one bar per day, today highlighted. A dashed line marks the daily allowance. Caption says how many days were over it.
- Top three apps with their time.
- Separate half-width cards under it: Not now (total, opened anyway, top reason) and Water · Walk (week totals).
- Words week strip: reviewed, learned and comeback counts, seven day cells filled on days with a review, "Lists" link.
- Saved for later: "3 this week".

Empty state: no usage permission: one line asking for it, with the action to grant it. No data for a range: show zeros, not blanks.
Data: `WellbeingCard.kt` and `WeekCard.kt` (`UsageRepository`, `HealthRepository`, `WalkCount`, `Prefs.pauseLog`, `WellbeingRanges`).

## Saved for later (full width)
- "Saved for later · 12", the latest item title, "Open" link.
- Empty state: "Nothing saved yet".
- Data: `SavedCard.kt`, `SavedItemsStore`.

## Next up (full width)
- Up to four upcoming calendar items: a time or day label on the left ("All day", "Tomorrow", "Mon"), the title on the right.
- Empty state: "Nothing coming up". Without calendar permission: one line asking for it.
- Data: `NextUpCard.kt` (calendar).

## Widget slot (full width, dashed border)
- "Your widget", "Widgets you add appear here", and a "+ Add" action.
- Once a widget is added it replaces this placeholder.
- Data: `TodayWidgets.kt`.

## Held (full width, warm tint)
- "Held · 1", latest held item with its app and time, the sender or title below, and a "Clear all" action.
- Empty state: the card is hidden.
- Data: `HeldCard.kt`, `HeldStore` (quiet mode).

## Notes on the mock-up
- The hour strip in the mock-up shows hour and temperature only. The design calls for an icon per hour as well.
- The "Example data" chip is only on the mock-ups.
