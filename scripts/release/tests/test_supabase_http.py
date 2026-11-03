#!/usr/bin/env python3
"""The content publisher retries a failure that is the server's, and not one that is ours."""
import sys
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import supabase_http


def error(code: int) -> urllib.error.HTTPError:
    return urllib.error.HTTPError("https://example.invalid", code, "nope", {}, None)


class Body:
    def __init__(self, data: bytes):
        self.data = data

    def read(self) -> bytes:
        return self.data

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class RetryTest(unittest.TestCase):
    def setUp(self):
        self.req = urllib.request.Request("https://example.invalid")
        patch = mock.patch.object(supabase_http.time, "sleep")
        self.addCleanup(patch.stop)
        self.sleep = patch.start()

    def call(self, side_effect):
        with mock.patch.object(supabase_http.urllib.request, "urlopen", side_effect=side_effect) as open_:
            return supabase_http.read(self.req), open_

    def test_a_good_response_is_returned_without_retrying(self):
        body, open_ = self.call([Body(b"[]")])
        self.assertEqual(b"[]", body)
        self.assertEqual(1, open_.call_count)
        self.sleep.assert_not_called()

    def test_the_522_that_broke_a_publish_is_retried_and_succeeds(self):
        body, open_ = self.call([error(522), error(522), Body(b"ok")])
        self.assertEqual(b"ok", body)
        self.assertEqual(3, open_.call_count)

    def test_a_dropped_connection_is_retried(self):
        body, open_ = self.call([urllib.error.URLError("connection reset"), Body(b"ok")])
        self.assertEqual(b"ok", body)
        self.assertEqual(2, open_.call_count)

    def test_a_rejected_request_is_not_retried_because_the_content_is_wrong(self):
        with self.assertRaises(urllib.error.HTTPError) as raised:
            self.call([error(400)])
        self.assertEqual(400, raised.exception.code)
        self.sleep.assert_not_called()

    def test_it_gives_up_after_the_last_attempt_rather_than_looping(self):
        with self.assertRaises(urllib.error.HTTPError):
            self.call([error(503)] * supabase_http.ATTEMPTS)
        self.assertEqual(supabase_http.ATTEMPTS - 1, self.sleep.call_count)

    def test_it_waits_longer_each_time(self):
        self.call([error(500), error(500), Body(b"ok")])
        waits = [c.args[0] for c in self.sleep.call_args_list]
        self.assertEqual(waits, sorted(waits))
        self.assertLess(waits[0], waits[1])


if __name__ == "__main__":
    unittest.main()
