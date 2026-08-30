# Goal reference

Every goal reads `./pom.xml` unless `-Dorganizer.pom` points somewhere else, and none of them
needs a project to be loaded first — they run in a directory with nothing but a POM in it.

- [Options every goal has](#options-every-goal-has)
- [Dependencies](#dependencies)
- [Dependency management and BOMs](#dependency-management-and-boms)
- [Properties](#properties)
- [Plugins](#plugins)
- [Modules](#modules)
- [Repositories](#repositories)
- [Profiles](#profiles)
- [Versions](#versions)
- [Organizing](#organizing)

## Options every goal has

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dorganizer.pom` | `./pom.xml` | The POM to work on |
| `-Dprofile` | | Work inside this profile instead of at the top level |
| `-DcreateProfile` | `false` | Create the profile named by `-Dprofile` when it does not exist |
| `-Dorganizer.interactive` | Maven's own setting | Force prompting on or off |

The goals that write have four more:

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dorganizer.dryRun` | `false` | Print a unified diff and write nothing |
| `-Dorganizer.backup` | `false` | Copy the POM aside before writing |
| `-Dorganizer.backupSuffix` | `.bak` | The suffix for that copy |
| `-Dorganizer.force` | `false` | Skip the confirmation before a destructive change |

The goals that take a coordinate accept it either as one string or in pieces:

```console
mvn organizer:add-dependency -Dartifact=org.junit.jupiter:junit-jupiter:5.11.4
mvn organizer:add-dependency -DgroupId=org.junit.jupiter -DartifactId=junit-jupiter -Dversion=5.11.4
```

`-Dartifact` is `groupId:artifactId[:version[:classifier[:type]]]`, and the individual parameters
win where both are given. Where a coordinate is used for matching rather than writing — the remove
goals, `-Dfilter` — `*` works as a wildcard in either segment: `-Dartifact='org.springframework.*:*'`.

## Dependencies

### `organizer:add-dependency`

Adds a `<dependency>`, creating `<dependencies>` if the POM has none. A coordinate that is already
declared is updated in place rather than duplicated, and any element added to an existing entry —
a `<version>`, a `<scope>` — goes into schema order rather than onto the end.

```console
mvn organizer:add-dependency -Dartifact=org.junit.jupiter:junit-jupiter:5.11.4 -Dscope=test
mvn organizer:add-dependency -Dartifact=com.acme:widget -Dexclusions=org.slf4j:slf4j-api
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dartifact` | | The coordinate, `groupId:artifactId[:version[:classifier[:type]]]` |
| `-DgroupId`, `-DartifactId`, `-Dversion` | | The coordinate in pieces |
| `-Dscope` | | `compile`, `provided`, `runtime`, `test` or `system` |
| `-Dtype` | | Packaging type, e.g. `pom` or `test-jar` |
| `-Dclassifier` | | Classifier, e.g. `tests` |
| `-Doptional` | | Write `<optional>true</optional>` |
| `-Dexclusions` | | `groupId:artifactId,groupId:artifactId` |
| `-DfailOnExisting` | `false` | Fail rather than update when the dependency is already declared |
| `-Dorganizer.resolveLatest` | `true` | Look the newest version up when none is given or managed |
| `-DallowSnapshots` | `false` | Consider `-SNAPSHOT` versions when resolving |

**Where the version comes from.** An explicit `-Dversion` wins. Failing that, a version declared in
this POM's `<dependencyManagement>`, then one managed by a parent POM or an imported BOM — in both
cases no `<version>` is written, which is the point of managing it. Failing all of those, the
newest release in your configured repositories. If that lookup is turned off or comes back empty,
the goal fails and says what to do about it.

### `organizer:remove-dependency`

Removes matching `<dependency>` entries and prunes a `<dependencies>` element left empty. The
version is ignored when matching, so you never have to look it up first. Run the goal without a
coordinate to pick from a list.

```console
mvn organizer:remove-dependency -Dartifact=org.junit.jupiter:junit-jupiter
mvn organizer:remove-dependency -Dartifact='org.springframework.*:*' -Dorganizer.force
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dartifact` and friends | | Which dependency, `*` allowed in either segment |
| `-DfailIfMissing` | `false` | Fail when nothing matches, instead of saying so |
| `-DremoveEmptyElements` | `true` | Remove a `<dependencies>` element left empty |

A comment sitting above a removed dependency stays where it is: the plugin will not guess that it
belonged to the entry.

### `organizer:list-dependencies`

Prints what the POM declares — not the resolved graph, which is what `dependency:tree` is for.
Never writes.

```console
mvn organizer:list-dependencies
mvn organizer:list-dependencies -Dscope=test -Dformat=table
mvn organizer:list-dependencies -Dfilter='org.springframework.*:*' -DincludeManaged
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dscope` | | Show only this scope |
| `-Dfilter` | | Show only coordinates matching this pattern |
| `-Dformat` | `plain` | `plain`, `table` or `tree` (grouped by scope) |
| `-DincludeManaged` | `false` | Also list `<dependencyManagement>` entries |

A dependency whose version is inherited shows as `(managed)` rather than blank.

## Dependency management and BOMs

### `organizer:add-managed-dependency`

The same as `add-dependency`, writing into `<dependencyManagement><dependencies>` and creating
both elements if needed. Takes the same parameters.

```console
mvn organizer:add-managed-dependency -Dartifact=com.google.guava:guava:33.4.0-jre
```

### `organizer:remove-managed-dependency`

The same as `remove-dependency`, on `<dependencyManagement>`. This is also how you remove an
imported BOM, which is just a managed entry with `<scope>import</scope>`.

```console
mvn organizer:remove-managed-dependency -Dartifact=com.google.guava:guava
```

### `organizer:import-bom`

Adds a managed entry with `<type>pom</type><scope>import</scope>`. Importing a BOM that is already
there updates its version rather than adding a second entry.

```console
mvn organizer:import-bom -Dbom=org.springframework.boot:spring-boot-dependencies:3.4.1
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dbom` | | The BOM coordinate; `-Dartifact` works too |
| `-Dorganizer.resolveLatest` | `true` | Look the newest version up when none is given |

## Properties

### `organizer:set-property`

Sets a property, creating `<properties>` if the POM has none. Updating an existing property
rewrites only its value, so a comment above it stays put.

```console
mvn organizer:set-property -Dproperty=maven.compiler.release -Dvalue=21
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dproperty` | | The property name |
| `-Dvalue` | | The value; when prompting, the current value is the default |

### `organizer:remove-property`

```console
mvn organizer:remove-property -Dproperty=spring.version
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dproperty` | | The property name; omit it to pick from a list |
| `-DfailIfMissing` | `false` | Fail when the property is not there |
| `-DremoveEmptyElements` | `true` | Remove a `<properties>` element left empty |

### `organizer:list-properties`

```console
mvn organizer:list-properties
mvn organizer:list-properties -Dfilter='maven.*'
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dfilter` | | Show only names matching this pattern, `*` as wildcard |

## Plugins

### `organizer:add-plugin`

Adds a plugin to `<build><plugins>`. The group defaults to `org.apache.maven.plugins`, and is left
out of the entry when it is that default — the way people write it by hand.

```console
mvn organizer:add-plugin -Dplugin=maven-surefire-plugin:3.5.2
mvn organizer:add-plugin -Dplugin=maven-surefire-plugin -Dconfiguration=skipTests=true,argLine=-Xmx1g
mvn organizer:add-plugin -Dplugin=maven-jar-plugin -Dexecutions=make-test-jar:package:test-jar
mvn organizer:add-plugin -Dplugin=org.jacoco:jacoco-maven-plugin:0.8.12 -DpluginManagement
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dplugin` | | `[groupId:]artifactId[:version]` |
| `-Dconfiguration` | | `key=value,key=value` |
| `-Dexecutions` | | `id:phase:goal1+goal2,id:phase:goal` |
| `-DpluginManagement` | `false` | Write into `<build><pluginManagement>` instead |
| `-Dorganizer.resolveLatest` | `true` | Look the newest version up when none is given |
| `-DallowSnapshots` | `false` | Consider `-SNAPSHOT` versions when resolving |

**Two-segment coordinates.** `a:b` could mean a group and an artifact or an artifact and a version.
It is read as artifact and version only when the first segment has no dot in it and the second
starts with a digit, so `maven-surefire-plugin:3.5.2` and `org.jacoco:jacoco-maven-plugin` both do
what you meant.

**Adding a plugin that is already declared** merges rather than duplicates: the version is updated,
configuration settings are merged one at a time so the ones you did not mention survive, and an
execution whose id is already there is not added twice.

### `organizer:remove-plugin`

```console
mvn organizer:remove-plugin -Dplugin=maven-surefire-plugin
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dplugin` | | Which plugin; omit it to pick from a list |
| `-DpluginManagement` | `false` | Work on `<pluginManagement>` instead |
| `-DfailIfMissing` | `false` | Fail when the plugin is not there |
| `-DremoveEmptyElements` | `true` | Remove `<plugins>` and `<build>` if left empty |

## Modules

### `organizer:add-module`

Adds a `<module>` to an aggregator, keeping the list sorted when it already is and appending when
it is not — a hand-ordered list is never shuffled behind your back.

```console
mvn organizer:add-module -Dmodule=my-service
mvn organizer:add-module -Dmodule=my-service -Dscaffold
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dmodule` | | The module directory, relative to the aggregator |
| `-DupdatePackaging` | `true` | Switch the aggregator's packaging to `pom` |
| `-Dscaffold` | `false` | Create the directory with a POM inheriting from the aggregator |

Scaffolding never overwrites a POM that is already there. The child's parent coordinates come from
the aggregator, falling back to the aggregator's own parent for anything it does not declare.

### `organizer:remove-module`

```console
mvn organizer:remove-module -Dmodule=my-service
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dmodule` | | Which module; omit it to pick from a list |
| `-DdeleteDirectory` | `false` | Also delete the directory, after confirming |
| `-DfailIfMissing` | `false` | Fail when the module is not listed |
| `-DremoveEmptyElements` | `true` | Remove a `<modules>` element left empty |

Files are never deleted unless you ask twice: once with the flag, once at the prompt. A dry run
deletes nothing.

## Repositories

### `organizer:add-repository`

Adds to `<repositories>`, or to `<pluginRepositories>` with `-DpluginRepository`. An id that is
already declared is updated rather than duplicated.

```console
mvn organizer:add-repository -Did=internal -Durl=https://repo.example.com/maven2
mvn organizer:add-repository -Did=snapshots -Durl=https://repo.example.com/snapshots \
    -Dreleases=false -Dsnapshots=true
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Did` | | The repository id, which `settings.xml` matches credentials against |
| `-Durl` | | The URL |
| `-Dname` | | A display name |
| `-Dlayout` | | The layout, when it is not `default` |
| `-Dreleases`, `-Dsnapshots` | | Write the `<enabled>` flags |
| `-DpluginRepository` | `false` | Work on `<pluginRepositories>` |

### `organizer:remove-repository`

```console
mvn organizer:remove-repository -Did=internal
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Did` | | Which repository; omit it to pick from a list |
| `-DpluginRepository` | `false` | Work on `<pluginRepositories>` |
| `-DfailIfMissing` | `false` | Fail when the id is not there |
| `-DremoveEmptyElements` | `true` | Remove a section left empty |

## Profiles

### `organizer:add-profile`

```console
mvn organizer:add-profile -Dprofile=ci -DactiveByDefault
mvn organizer:add-profile -Dprofile=release -DactivationProperty=performRelease=true
mvn organizer:add-profile -Dprofile=modern -DjdkActivation='[17,)'
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dprofile` | | The profile id |
| `-DactiveByDefault` | `false` | Activate unless another profile is chosen |
| `-DactivationProperty` | | `name` or `name=value` |
| `-DjdkActivation` | | A JDK version or range |

Running it again on a profile that exists replaces its activation and leaves the rest alone.

### `organizer:remove-profile`

Removes the profile and everything declared inside it, after confirming.

```console
mvn organizer:remove-profile -Dprofile=ci
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-Dprofile` | | Which profile; omit it to pick from a list |
| `-DfailIfMissing` | `false` | Fail when the profile is not there |
| `-DremoveEmptyElements` | `true` | Remove a `<profiles>` element left empty |

### `organizer:list-profiles`

Prints each profile, how it activates, and a count of what it holds.

```console
$ mvn organizer:list-profiles
ci
  activation: active by default
  contains:   1 dependency
modern
  activation: jdk [17,)
  contains:   2 properties
```

## Versions

### `organizer:set-version`

```console
mvn organizer:set-version -DnewVersion=1.2.0
mvn organizer:set-version -Dbump=minor
mvn organizer:set-version -DreleaseVersion             # 1.2.3-SNAPSHOT -> 1.2.3
mvn organizer:set-version -DnextSnapshot               # 1.2.3          -> 1.2.4-SNAPSHOT
mvn organizer:set-version -DnewVersion=2.0.0 -DupdateChildren
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-DnewVersion` | | The version to set |
| `-Dbump` | | `major`, `minor` or `patch` |
| `-DreleaseVersion` | `false` | Drop the `-SNAPSHOT` suffix |
| `-DnextSnapshot` | `false` | Bump the patch segment and add `-SNAPSHOT` |
| `-DupdateParent` | `false` | Update `<parent><version>` when it matches the old version |
| `-DupdateChildren` | `false` | Rewrite every module's parent reference, recursively |

Exactly one of the first four, please: passing several is refused rather than silently resolved.
Bumping keeps the qualifier and the snapshot suffix, so `1.2.3-RC1-SNAPSHOT` bumped by minor is
`1.3.0-RC1-SNAPSHOT`.

Children go through the same write path as the POM you named, so `-Dorganizer.dryRun` writes none
of them and `-Dorganizer.backup` backs all of them up.

## Organizing

### `organizer:organize`

Puts the top-level sections in the order the Maven POM reference recommends and recurses, so
`<dependency>`, `<plugin>`, `<build>`, `<profile>` and `<execution>` get their children in schema
order too. Then it sorts the lists.

```console
mvn organizer:organize
mvn organizer:organize -DsortDependencies=scope,groupId,artifactId
mvn organizer:organize -DcheckOnly
```

| Parameter | Default | What it does |
| --- | --- | --- |
| `-DreorderSections` | `true` | Put the sections in schema order |
| `-DsortDependencies` | `groupId,artifactId` | Sort key, or `false` to leave them alone |
| `-DsortPlugins` | `true` | Sort `<plugins>` by coordinate |
| `-DsortModules` | `true` | Sort `<modules>` alphabetically |
| `-DsortProperties` | `true` | Sort `<properties>` by name |
| `-DkeepBlankLines` | `true` | Keep the blank lines between entries |
| `-DcheckOnly` | `false` | Report and fail instead of writing |

`-DsortDependencies` takes any comma-separated list of `<dependency>` child element names, so
`scope,groupId,artifactId` groups by scope first.

**What moves and what does not.** Reordering moves whole blocks: a comment travels with the element
written below it, and the blank line separating two entries goes with the block it precedes —
dropped only when that block ends up first, since no POM wants an empty line straight after an
opening tag. Two things are deliberately left alone: `<configuration>`, where the elements are a
plugin's own vocabulary rather than POM structure, and any section with mixed content or a trailing
comment there is no sensible way to attach.

Running the goal twice changes nothing the second time.
