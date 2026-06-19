# GameTest fixtures

`empty.snbt` is the empty structure template required by the headless GameTests in
`AttributeLimitGameTests`. The test framework loads it from the run directory's
`gameteststructures/` folder, which is gitignored — so this versioned copy is the source of truth.

To run the tests, copy the template into place once:

```
neoforge/gametest/empty.snbt  ->  neoforge/run/gameteststructures/empty.snbt
```

Then launch `:neoforge:runClient`, open a world, and run `/test runall` (or
`/test run attributefix:maxclamplowersvalue`).
