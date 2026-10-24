# Notes: open list

Mock-up: `mockups/Notes.dc.html`. Plan and build order: issue #456.

Top to bottom:

1. Title "Notes" with a count line ("4 open · 1 pinned").
2. Search field "Search notes".
3. **Pinned** section. Each pinned note is a card with a title. A checklist note shows tick boxes (ticked items are greyed and struck through), a progress line ("1 of 3 done") and an edited-at label ("edited today"). The pin marker is a small pin icon at the top right of the card. There is no coloured edge on the left (the green left edge was removed).
4. **Open** section. Plain notes show the text and the date under it, with a "Due" chip when dated. Checklist notes show tick boxes, progress ("2 of 4 done") and the edited-at label ("edited yesterday").
5. "New note" bar at the bottom. It hints at the checklist syntax: `[] Shopping: milk, eggs`.

Rules:
- Edited-at labels: "edited today", "edited yesterday", or "edited Fri 24 Oct".
- Empty state: "No notes yet" above the "New note" bar. Empty pinned section is hidden.
- Search with no hits: "No matching notes".
- Data: `helper/Notes.kt`, `NotesStore` (`NOTES_JSON`).
