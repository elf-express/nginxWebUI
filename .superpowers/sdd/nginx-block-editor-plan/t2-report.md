# T2 report: persistence and save contracts

## RED / GREEN evidence

- RED: `mvn -o test -Dtest=BlockEditorPersistenceTest` exited 1 before implementation. Test compilation failed on the missing mode accessors and save/reload service APIs (expected missing-feature failure).
- GREEN: after adding schema round-trip coverage, `mvn -o test -Dtest=BlockEditorPersistenceTest` exited 0; 9 tests passed, 0 failures, 0 errors.
- The focused tests cover legacy-mode defaults, exact raw-text save/reload, failed-save preservation, explicit upstream conversion, global upstream name uniqueness, repeated Stream `server` blocks, guide replacement preserving block rows, legacy API overwrite protection, monitoring/connection-test exclusions, and a real SQLite schema round-trip.
- `git diff --check` passed.
- Full `mvn -o test` exited 0: 167 tests passed, 0 failures, 0 errors. This included T2 persistence 9/9, T3 configuration 5/5, and codec 12/12.
- T4's targeted Playwright E2E run passed 3/3, exit 0; its package command passed and the test server was stopped.

## Contracts implemented

- `configMode` and `rawContent` are nullable model fields. A null mode reads as `legacy`, so existing rows require no data rewrite or default backfill.
- `POST /adminPage/upstream/saveBlock(id,name,proxyType,rawContent,convertLegacy)` validates with the T1 codec, enforces global name uniqueness, requires explicit conversion for legacy rows, and disables node monitoring on conversion.
- `POST /adminPage/stream/saveBlock(id,header,rawContent)` validates a `stream-child` block. Repeated `server` headers remain legal.
- Detail responses expose mode and raw content. `parseBlock` returns `{header,body}`; `blockPreview` serializes the effective legacy upstream block through the existing `ConfService.buildBlockUpstream` path and `NgxDumper`.
- Legacy admin/API updates reject an existing block row. Legacy upstream/stream creation remains supported. `StreamService.setAll` only replaces matching legacy rows.
- Scheduled node monitoring and upstream connection tests omit block-mode legacy server rows.

## Limitations / remaining verification

- Diagnostic line and column are included in the localized parser error message. The shared `JsonResult` wire shape remains unchanged; it does not expose separate `line`/`column` properties.
- T3 made a final test-only CRLF assertion change while the full suite was running and is verifying it with a focused rerun; the completed full suite passed the code/test state compiled at its start.
