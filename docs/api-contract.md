# EnglishMaster AI — API contract (phase 2)

Base URL: `https://<project-ref>.supabase.co`. Every request carries `apikey: <anon key>`.
Authenticated requests also carry `Authorization: Bearer <access_token>`. The anon key is
public; all authorisation is enforced server-side (RLS + SECURITY DEFINER checks).

The Android client implementation is in `android/core/network` (`Apis.kt`, `Dtos.kt`, `ApiResult.kt`).

## Auth (Supabase GoTrue)

| Call | Request | Success | Notes |
|---|---|---|---|
| `POST /auth/v1/signup` | `{email, password, data:{display_name}}` | user object (confirmation required) **or** session | Any `role` in metadata is ignored server-side; every account starts as `student`. |
| `POST /auth/v1/token?grant_type=password` | `{email, password}` | `{access_token, refresh_token, expires_in, expires_at?, user:{id,email}}` | |
| `POST /auth/v1/token?grant_type=refresh_token` | `{refresh_token}` | same as above | Client refreshes 60 s before expiry and once on HTTP 401; a 4xx response signs the user out. |
| `POST /auth/v1/recover` | `{email}` | `{}` | Same UI response whether or not the account exists. |
| `POST /auth/v1/logout` | bearer token | 204 | Best effort; the client always clears its session. |

Auth error mapping (`error_code` / legacy `error`): `invalid_credentials`/`invalid_grant` →
wrong credentials; `email_not_confirmed`; `user_already_exists`/`email_exists`; `weak_password`;
`signup_disabled`; `over_*` or HTTP 429 → rate limited.

## RPCs (PostgREST, `POST /rest/v1/rpc/<name>`, JSON body)

### `get_practice_questions`
Request: `{"p_cefr_level":"A1","p_after":null,"p_limit":100}` (`p_after` = last id of previous page; omit for first page; `p_limit` 1–200)
Response: `[{"id":uuid,"cefr_level":"A1","skill":"grammar","prompt":"…","options":["…","…"],"version":1,"review_status":"unreviewed","updated_at":"…"}]`
Never contains the answer key.

### `submit_practice_response`
Request:
```json
{"p_response_id":"<client uuid>","p_attempt_id":"<client uuid>","p_question_id":"<uuid>",
 "p_chosen_option":"B","p_client_answered_at":"2026-10-19T10:00:00Z"}
```
Response (array with one row):
```json
[{"response_id":"<uuid>","is_correct":true,"correct_option":"B","explanation":"…",
  "received_at":"<server time>","duplicate":false}]
```
* Retrying with the same `p_response_id` returns the stored result with `duplicate: true`; no new row.
* A second answer to the same question in the same attempt returns the **first** stored answer (`duplicate: true`).
* `p_client_answered_at` is informational; values more than 5 min in the future are dropped. `received_at` is authoritative.

### `get_my_practice_summary`
Request `{}` → `[{"cefr_level":"A1","answered":12,"correct":9}]`

### `update_my_profile`
Request `{"p_display_name":"…","p_interface_language":"ckb"}` (either may be null) → updated profile row.

## Errors

PostgREST returns `{"code":"<SQLSTATE>","message":"…"}`. The client maps by `code`, never shows `message`:

| SQLSTATE / HTTP | Meaning | Client behaviour |
|---|---|---|
| `28000`, `PGRST301`, `PGRST303`, HTTP 401 | not authenticated / JWT expired | refresh once, else sign in again |
| `42501` | forbidden (suspended account, not owner, anon) | stop sync, keep local data, inform user |
| `22023`, `22P02`, `23xxx` | invalid input / question not available | mark that answer as rejected, continue with the next |
| `54000`, HTTP 429 | rate limited | retry later (WorkManager backoff) |
| HTTP 5xx, network errors | transient | retry later |
