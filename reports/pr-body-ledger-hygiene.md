Bookkeeping only, no code.

- **00.14** row marked SUPERSEDED: it was reopened 2026-11-15 and closed by the rework row (PR #37) the next day, but the totals line still counted it IN PROGRESS. Totals now read **65 COMPLETE / 1 IN PROGRESS / 11 SECTION REVIEW / 13 PLANNED = 90**, with PLANNED listing its five named externally-gated sections plus chunk-8 (08.01-08.08).
- **07.03** deviation closed: 06.06 ClickHouse marts now exist (PR #141), so "marts not available" is no longer true. The twin still reads operational tables, but by design — a capacity answer must be live, and the marts refresh on the nightly sync. The section stays SECTION REVIEW for the two real gaps (staffing/demand forecasting, true 30-day event replay), and its row no longer says "full suite pending".
