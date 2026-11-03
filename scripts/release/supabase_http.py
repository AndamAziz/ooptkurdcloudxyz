#!/usr/bin/env python3
"""One HTTP call to Supabase, retried when the failure is not ours.

A content push once failed with "HTTP Error 522" from Cloudflare in front of Supabase: the
request never reached the database, nothing was wrong with the content, and the two later steps
of the workflow were skipped because the first one had exited. A blip like that should cost a few
seconds, not a whole publish, so the transient ones are retried with a growing wait.

A 4xx is not retried: that is the content being wrong, and trying again would only hide it.
"""
import time
import urllib.error
import urllib.request

# 408 request timeout, 429 too many requests, and the 5xx range: the server's problem, not ours.
RETRY_STATUS = frozenset({408, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524})
ATTEMPTS = 5
BACKOFF = 2.0


def read(req: urllib.request.Request, timeout: int = 60, attempts: int = ATTEMPTS) -> bytes:
    """Sends [req] and returns the body, retrying a transient failure up to [attempts] times."""
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as res:
                return res.read()
        except urllib.error.HTTPError as e:
            if e.code not in RETRY_STATUS or attempt == attempts:
                raise
            detail = f"HTTP {e.code}"
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            if attempt == attempts:
                raise
            detail = f"{type(e).__name__}: {e}"
        wait = BACKOFF ** attempt
        print(f"  {detail}; retrying in {wait:.0f}s ({attempt} of {attempts - 1})", flush=True)
        time.sleep(wait)
    raise AssertionError("unreachable")
