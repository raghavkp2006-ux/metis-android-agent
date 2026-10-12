# V13 local-calendar regression record

Status: development candidate, ML **NO_GO**, no fresh independent evaluation. The v12 classifier and its previously reported F1 values were not retrained or rescored by this calendar change.

| Check | Result |
| --- | --- |
| Calendar questions from the 50-query exposed user batch | 4 admitted as local calendar reads; complete scopes retained |
| Existing memory reads in that batch | 4 retained |
| Other requests in that batch | 42 still blocked before context/read |
| Earlier exposed semantic-calendar requests with unsupported constraints or references | 23 still blocked before context/read, including a forced calendar label |
| Earlier exposed clarify/deny requests | Blocked before context/read, including a forced calendar label |
| New complete calendar form variants | 8 positive forms in JVM scope test; meeting and appointment type constraints retained |
| Paired unsupported calendar variants | 10 blocked before context/read with forced calendar label |
| Editor validity | date/time ordering, invalid dates, DST gaps and overlaps checked |
| Historical Python behavior | 42 tests passed with frozen artifacts checked |
| Forged calendar extraction | Blocked before context/read on all eight forms |
| Full JVM regression | 126 tests passed locally; seven v13 calendar methods |
| Detekt, debug/release lint and APK builds | Passed locally |
| Android API 34 emulator | 6 targeted tests passed: five storage/agent tests and one add/edit/delete UI flow |
| Physical phone | Unexecuted for v13; device disconnected before installation |
| Integrity | 109 frozen artifact files unchanged; current source certificate uses normalized text hashes for checkout portability |

The adapter reads only accepted encrypted METIS schedule blocks. Repository fixtures include matching and decoy person/topic records, proposed blocks, time windows, no-match responses, and unchanged stored data. A 102-record fixture verifies the 101-row database fetch and 203-decryption bound (101 title/reason pairs plus a key probe), and confirms that only five results are displayed with an incompleteness notice. Standalone event editing preserves the plan identifier and increments the revision; deleting it leaves a separate planner record unchanged. The enabled `ConfirmedTaskAgent` path answers an upcoming-meetings query and blocks its appended-call variant without decrypting any stored record. The screen test uses a fake repository and does not clear installed user data.

The initial emulator run passed all five storage cases but could not launch the UI test's default activity. The UI test now uses the project's existing isolated debug activity; the complete rerun passed all six cases. The earlier emulator failed to complete startup; a fresh test-only API 34 AVD was used for the passing run. Logs and screenshots remain in ignored `app/build/reports/phase15/v13/`. These results do not claim physical-phone validation or Android model resource measurements.

The earlier v12 Python rules still describe calendar as unavailable because they certify that frozen candidate. V13's Android app enables the local calendar adapter; the new Kotlin tests measure that path. Historical v6–v12 artifacts and the first human evaluation are not modified. No system-calendar sync, natural-language event creation, device model inference, or independent F1 evaluation is claimed.
