# Organizer Maven Plugin

Manage `pom.xml` from the command line — add and remove dependencies, plugins, properties,
modules, repositories and profiles, and keep the file organized — without a text editor and
without reformatting the file.

```console
$ mvn organizer:add-dependency -Dartifact=org.apache.commons:commons-lang3
[INFO] Resolved the newest release version of org.apache.commons:commons-lang3 to 3.20.0
[INFO] Added dependency org.apache.commons:commons-lang3:3.20.0
[INFO] Updated /home/you/project/pom.xml (5 lines changed)
```

Five lines changed, and they are the five lines you wanted:

```diff
   </dependencies>
+    <dependency>
+      <groupId>org.apache.commons</groupId>
+      <artifactId>commons-lang3</artifactId>
+      <version>3.20.0</version>
+    </dependency>
```

## Why it exists

Editing a POM by hand is fine until you are doing it in a script, across twenty repositories, or
from an agent. The obvious alternatives each cost something: `sed` does not understand XML, and
tools that parse the POM into a model and write it back reformat the whole file, so a one-line
change arrives as a four-hundred-line diff with your comments stripped out.

This plugin edits the XML in place. Your comments, blank lines, indentation — tabs included — the
multi-line `<project>` tag and even the byte-order mark all survive, because the only bytes it
rewrites are the ones the change actually touches. A POM it did not need to change comes back
byte-for-byte identical.

## Requirements

Maven 3.6 or newer, and Java 17 or newer.

## Installing

Nothing to install: invoke it by coordinate.

```console
mvn io.github.pgatzka:organizer-maven-plugin:1.0.0:add-dependency -Dartifact=g:a:1.0
```

For the short `organizer:` prefix, add the group to `~/.m2/settings.xml` once:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>io.github.pgatzka</pluginGroup>
  </pluginGroups>
</settings>
```

Then:

```console
mvn organizer:add-dependency -Dartifact=g:a:1.0
```

To bind the `organize` goal into a build, declare the plugin as usual — see
[docs/recipes.md](docs/recipes.md#keeping-poms-organized-in-ci).

## The goals

| Goal | What it does |
| --- | --- |
| [`organizer:add-dependency`](docs/goals.md#organizeradd-dependency) | Add or update a `<dependency>` |
| [`organizer:remove-dependency`](docs/goals.md#organizerremove-dependency) | Remove a `<dependency>` |
| [`organizer:list-dependencies`](docs/goals.md#organizerlist-dependencies) | Print the declared dependencies |
| [`organizer:add-managed-dependency`](docs/goals.md#organizeradd-managed-dependency) | Add an entry to `<dependencyManagement>` |
| [`organizer:remove-managed-dependency`](docs/goals.md#organizerremove-managed-dependency) | Remove an entry from `<dependencyManagement>` |
| [`organizer:import-bom`](docs/goals.md#organizerimport-bom) | Import a BOM |
| [`organizer:set-property`](docs/goals.md#organizerset-property) | Set a build property |
| [`organizer:remove-property`](docs/goals.md#organizerremove-property) | Remove a build property |
| [`organizer:list-properties`](docs/goals.md#organizerlist-properties) | Print the declared properties |
| [`organizer:add-plugin`](docs/goals.md#organizeradd-plugin) | Add or merge into a build plugin |
| [`organizer:remove-plugin`](docs/goals.md#organizerremove-plugin) | Remove a build plugin |
| [`organizer:add-module`](docs/goals.md#organizeradd-module) | Add a module, optionally scaffolding it |
| [`organizer:remove-module`](docs/goals.md#organizerremove-module) | Remove a module |
| [`organizer:add-repository`](docs/goals.md#organizeradd-repository) | Add or update a repository |
| [`organizer:remove-repository`](docs/goals.md#organizerremove-repository) | Remove a repository |
| [`organizer:add-profile`](docs/goals.md#organizeradd-profile) | Add a profile, with activation |
| [`organizer:remove-profile`](docs/goals.md#organizerremove-profile) | Remove a profile |
| [`organizer:list-profiles`](docs/goals.md#organizerlist-profiles) | Print the profiles and how they activate |
| [`organizer:set-version`](docs/goals.md#organizerset-version) | Change the project version |
| [`organizer:organize`](docs/goals.md#organizerorganize) | Put the POM in schema order and sort its lists |

Full parameter reference: [docs/goals.md](docs/goals.md). Worked examples:
[docs/recipes.md](docs/recipes.md).

## Four things worth knowing

### The version is optional

Leave the version off and the plugin works out what it should be, in this order: a version your
`<dependencyManagement>` declares, a version a parent POM or an imported BOM manages, and failing
both, the newest release in your configured repositories.

```console
mvn organizer:import-bom -Dbom=org.springframework.boot:spring-boot-dependencies:3.4.1
mvn organizer:add-dependency -Dartifact=org.springframework.boot:spring-boot-starter-web
```

The second command writes no `<version>` at all, because the BOM now manages it.

### Every goal is interactive

Run a goal with no arguments and it asks:

```console
$ mvn organizer:add-dependency
groupId: org.assertj
artifactId: assertj-core
Version for org.assertj:assertj-core [3.27.6]:
Scope:
  1) (none, the default compile scope)
  2) compile
  3) provided
  4) runtime
  5) test
  6) system
Choose 1-6 [1]: 5
[INFO] Added dependency org.assertj:assertj-core:3.27.6 (test)
```

The remove goals list what is there and take a number, so nothing needs retyping. Under `mvn -B`
nothing prompts: goals fail with a message naming the parameter to pass, which is what you want in
CI.

### Look before you leap

`-Dorganizer.dryRun` prints the diff and writes nothing; `-Dorganizer.backup` copies the file to
`pom.xml.bak` first. A request that changes nothing does not rewrite the file at all, so
timestamps and build caches stay put.

```console
$ mvn organizer:remove-dependency -Dartifact=junit:junit -Dorganizer.dryRun
[INFO] Dry run, not writing /home/you/project/pom.xml:
[INFO] --- pom.xml
[INFO] +++ pom.xml
[INFO] @@ -22,10 +22,6 @@
[INFO] -    <dependency>
[INFO] -      <groupId>junit</groupId>
[INFO] -      <artifactId>junit</artifactId>
[INFO] -      <version>4.13.2</version>
[INFO] -    </dependency>
```

### Anything can go in a profile

`-Dprofile=<id>` sends any of the write goals into that profile instead of the top level.

```console
mvn organizer:add-dependency -Dartifact=com.h2database:h2 -Dprofile=local -DcreateProfile
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: `mvn verify` for the unit tests and the coverage
gate, `mvn verify -Prun-its` for the integration tests, `mvn verify -Pquality` for SpotBugs and
Checkstyle.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
