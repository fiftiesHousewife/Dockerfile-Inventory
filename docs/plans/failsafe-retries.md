# Retries on Failsafe: plan

Status: planned 12 Nov; questions answered 12 Nov; done 12 Nov, tests 1-6: `RetryPolicy.retryable`, `delay`,
`backoff` (`firstBackoff`), `Retries`, both senders through it with every test waiting zero (the slowest of
discovery's tests 0.4 s), `Slowdown`. Test 7, the measurement, waits on the owner's run: this sandbox cannot reach
GitHub.

## Why

Asked 12 Nov ("surely there is a library solution for this throttle"): `http` hand-writes what a library does. Two
loops, `GitHubSender.send` and `ArtifactoryClient.send`, each retry with backoff, count attempts and sleep; the
project's rule is libraries first. Failsafe (`dev.failsafe:failsafe`, Apache 2.0, no dependencies) is the standard
for retries on the JVM.

## The design

- **What moves**: the retry loop, the attempt count and the sleeping between attempts, into a Failsafe
  `RetryPolicy<HttpResponse<String>>` built in one place, `http`'s `Retries`, from our `RetryPolicy`'s decisions.
  Both senders run each call through it.
- **What stays ours**, as no library knows it: which answers are worth another try and how long to wait (a
  gateway's 502, 503, 504; a 429; a 403 with rate-limit headers; `retry-after`; `x-ratelimit-reset`; the longest
  wait, `RateLimitWaitTooLong`), the token read again after a 401 (once, not counted as an attempt), GitHub's SSO
  refusal, the pacing before a rate limit runs out (`RatePacing`, after an answer that is not retried) and
  `Throttle`.
- **`RetryPolicy`** splits its one question in two, as Failsafe asks them: `retryable(status, headers)` and
  `delay(status, headers, attempt, now)` (and `backoff(attempt)` for a network error). It gains `firstBackoff`
  (one second unless given), so tests that retry pass zero: Failsafe sleeps for real, with no clock to swap.
- **The wait logged**: Failsafe's `onRetryScheduled` gives each wait; the senders log it at debug, and discovery's
  of 30 seconds or more at info, as now.
- **`Throttle`** keeps its own spacing (its rate changes as the server slows, which Failsafe's `RateLimiter` cannot
  do), and the slowing down moves to `Slowdown`, so each class is under 150 lines.
- **`Sleeper`** stays for `RatePacing` and `Throttle`; `ArtifactoryClient`, which only slept between retries, loses
  it.

## Tests, in the order they will be written

1. `RetryPolicy.retryable`: 502, 503, 504 and 429 yes; 403 only with rate-limit headers; 200, 404, 401 no.
2. `RetryPolicy.delay`: `retry-after`, `x-ratelimit-reset`, backoff otherwise; beyond the longest wait refused.
3. `RetryPolicy.backoff` from `firstBackoff`: doubling, capped at a minute, zero stays zero.
4. `Retries`: a network error then an answer is the answer; a 502 then a 200 is the 200; the attempts spent on a
   502 return the 502; spent on network errors throw the last error; a wait too long throws
   `RateLimitWaitTooLong`; each wait is told.
5. `GitHubSender.send` and `ArtifactoryClient.send` through `Retries`: the existing tests, with zero waits, green.
6. `Slowdown`: `Throttle`'s halving and doubling tests, moved.
7. Measured: `./gradlew :discovery:githubLive` (the owner's run) and the try over REST on `sclorg`, as before.

## What the answers settle

- **Retries on Failsafe only**: `Throttle`'s spacing stays ours, since Failsafe's `RateLimiter` cannot change its
  rate once built.
- **Tests wait zero**: fake servers' answers retried with `firstBackoff` zero and `retry-after: 0`; the waits'
  lengths are tested on `RetryPolicy` alone.
