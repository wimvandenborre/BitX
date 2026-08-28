# BitX Controller API 25 audit

BitX previously compiled against Controller API 25 while declaring API 18. It now declares API 25,
matching the compile-time dependency, and a clean API 25 build reports no deprecated API use.

The clip launcher now registers one playback observer per track instead of one duplicate observer per
scene. Queued launcher transitions retrigger commands on an already-playing clip without running them
twice when Bitwig also reports a new playing-state transition.

Bitwig's API 25 `TrackBankFlatteningMode` overloads are marked beta, so BitX keeps its stable existing
track-bank API. A useful next step is extracting command parsing and launcher state into small classes
with unit tests; the current Maven project has no automated tests.
