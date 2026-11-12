# Discovery's progress in the log: plan

Status: planned 12 Nov; questions answered 12 Nov; done 12 Nov: tests 1 and 3-6, and `ProgressLog` quiet while
the run asks whether to go on (`Phase.ASKING`). Test 2 dropped: `Progress` is synchronized, so a test from several
threads would test the JDK.

## Why

Asked 12 Nov, after a try over REST on `sclorg`: discovery logs a line when it starts and its summary when it ends,
and nothing between. The try was silent for three minutes; on a 15,000-repository estate over REST it would be silent
for hours, and a rate-limit wait of up to an hour (`MAX_RATE_LIMIT_WAIT`) is silent too, so a working run cannot be
told from a stuck one.

## The design

- `Progress`: what the run is doing, safe to update from the scanning threads: the phase (listing repositories,
  scanning repositories, fetching Dockerfiles, fetching YAML, reading last changes), done and total for it,
  Dockerfiles found so far, and when the run started. `line()` writes it as one sentence:
  `discovery: scanning repositories, 40 of 75, 120 Dockerfiles found, 310 calls, 2m 30s in`.
- The calls: `Throttle` counts the calls it records (`calls()`), as every call to GitHub passes through it.
- `ProgressLog`: logs `Progress.line()` at info every 30 seconds on a daemon thread, from the start of the run to its
  end (`AutoCloseable`, closed in `DiscoveryMain` whether the run ends or fails). A run shorter than 30 seconds
  logs no progress line.
- Updated where the work is done: `Discovery.repositories` per organisation listed, `Scans.scanOne` per repository
  (with its Dockerfiles), `BlobFetch` and `ChangeFetch` per batch.
- A rate-limit wait of 30 seconds or more is logged at info when it starts, with how long and until when:
  `discovery: GitHub's rate limit is spent, waiting 41m until 08:15Z` (today it is a debug line).

## Tests, in the order they will be written

1. `Progress.line()`: the phase, done of total, found, calls and time in, from a fixed clock.
2. `Progress` from several threads: every repository counted.
3. `Throttle.calls()`: one a recorded call.
4. `ProgressLog`: its task logs the line; closing it stops the schedule.
5. `Discovery` against `FakeGitHub`: the phases in order, each with its total.
6. `GitHubSender.pause`: a wait of 30 seconds or more logged at info, a shorter one at debug.

## What the answers settle

- **A line every 30 seconds**, not one a repository: readable on a large estate.
