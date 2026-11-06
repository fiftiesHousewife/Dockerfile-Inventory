Contracts between modules that hand each other files. Each is checked by a test on both sides, so the two
implementations of one normal form cannot drift apart without a test failing.

`python-311.steps.json`: the normal-form steps of a real Dockerfile and of the real build history of the image
built from it. `facts` must produce these from `facts/src/test/resources/fixtures/dockerfiles/python-311.Dockerfile`
(upstream `sclorg/s2i-python-container`, commit `b30d258`, `3.11/Dockerfile.rhel9`). `lineage` must produce them from
`lineage/src/test/resources/fixtures/history/ubi9-python-311.json` (the public `ubi9/python-311` image), with
builder-added steps removed.
