# Contributing

## Building

```console
mvn verify                 # unit tests and the coverage gate
mvn verify -Prun-its       # integration tests: real Maven, real projects, under src/it
mvn verify -Pquality       # SpotBugs and Checkstyle
```

All three run in CI, as separate jobs, so a red build points at one thing.

## How the code is laid out

```
core/    Everything that edits XML. No Maven types, so it is testable on its own.
mojo/    The goals. Thin: parse parameters, call core, report.
```

`core` is where the interesting parts are:

- `PomDocument` — load and save without disturbing anything untouched. It re-serializes only the
  document element and carries the XML declaration, the DOCTYPE, the outer comments, the original
  `<project>` start tag, the encoding, the byte-order mark and the line separator across verbatim.
- `XmlBoundaries` — finds where the document element starts and ends, honouring comments, CDATA,
  processing instructions, a DOCTYPE with an internal subset, and quoted attribute values.
- `Poms` — namespace-tolerant navigation, and mutation that indents to match the document.
- `PomOrganizer` — the block moving behind `organize`.

## Rules worth knowing before you change something

**A POM that was not changed must come back byte-for-byte identical.** `PomDocumentTest` holds
that line for LF and CRLF, tabs and spaces, byte-order marks, and non-UTF-8 encodings. If you
touch serialization, that suite is the one to watch.

**Build detached subtrees with `addContent`, then append once.** `Poms.append` and
`Poms.insertBefore` compute indentation from the parent's depth, which is zero for an element that
is not in the document yet. They throw if you try, rather than quietly mis-indenting.

**Goals stay thin.** Logic lives in `core`, where it can be tested without a Maven session. A mojo
that needs more than parameter handling and a couple of calls is a sign something belongs in
`core`.

**Mojo fields are package-private on purpose.** The tests set them directly and call `execute()`,
which is far less machinery than the plugin testing harness for the same coverage.

## Adding a goal

1. Write the behaviour in `core`, with tests.
2. Add the mojo, extending `AbstractPomWriteMojo` (or `AbstractPomMojo` if it only reads).
3. Add a test class extending `MojoTest`. Cover the happy path, the "already done" case, the
   "nothing there" case, the interactive path with `ScriptedPrompter`, and batch mode.
4. Document it in `README.md` and `docs/goals.md`. CI checks that every goal appears in both.
5. If it is worth exercising through real Maven, add a project under `src/it`.

## Style

Checkstyle covers the mechanical parts. Beyond that: 4-space indent, 120-column lines, and
comments that say why rather than what. Javadoc on every goal and parameter — it becomes the
plugin documentation, and CI fails on an empty description.
