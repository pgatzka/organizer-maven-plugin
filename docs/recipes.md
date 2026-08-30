# Recipes

Worked examples. Each one is something you would otherwise do by opening the file.

- [Starting a project from an empty POM](#starting-a-project-from-an-empty-pom)
- [Adopting a BOM](#adopting-a-bom)
- [Splitting a project into modules](#splitting-a-project-into-modules)
- [Cutting a release](#cutting-a-release)
- [Keeping POMs organized in CI](#keeping-poms-organized-in-ci)
- [Changing many repositories at once](#changing-many-repositories-at-once)
- [Adding an internal repository](#adding-an-internal-repository)
- [Working in a profile](#working-in-a-profile)
- [Driving it from a script or an agent](#driving-it-from-a-script-or-an-agent)

## Starting a project from an empty POM

```console
cat > pom.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>demo</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</project>
EOF

mvn organizer:set-property -Dproperty=maven.compiler.release -Dvalue=21
mvn organizer:add-dependency -Dartifact=org.junit.jupiter:junit-jupiter -Dscope=test
mvn organizer:add-dependency -Dartifact=org.assertj:assertj-core -Dscope=test
mvn organizer:add-plugin -Dplugin=maven-surefire-plugin
```

Neither dependency needed a version: both were resolved from Maven Central. Neither did the
plugin.

## Adopting a BOM

Import the BOM, then drop the versions the BOM now owns.

```console
mvn organizer:import-bom -Dbom=org.springframework.boot:spring-boot-dependencies:3.4.1
mvn organizer:add-dependency -Dartifact=org.springframework.boot:spring-boot-starter-web
mvn organizer:add-dependency -Dartifact=org.springframework.boot:spring-boot-starter-test -Dscope=test
```

The starters are written without a `<version>`, because the plugin can see the BOM manages them.
To check what you ended up with:

```console
mvn organizer:list-dependencies -DincludeManaged -Dformat=table
```

## Splitting a project into modules

```console
mvn organizer:add-module -Dmodule=demo-core -Dscaffold
mvn organizer:add-module -Dmodule=demo-web -Dscaffold
```

The aggregator's packaging switches to `pom` on the first module, and each child gets a POM
inheriting from it. Move the sources across, then give a child its own dependency:

```console
mvn organizer:add-dependency -Dorganizer.pom=demo-web/pom.xml \
    -Dartifact=org.springframework.boot:spring-boot-starter-web
```

## Cutting a release

```console
mvn organizer:set-version -DreleaseVersion -DupdateChildren
mvn verify
git commit -am "Release 1.2.3" && git tag v1.2.3

mvn organizer:set-version -DnextSnapshot -DupdateChildren
git commit -am "Back to development"
```

Check what it will do first, across the whole reactor, without writing anything:

```console
mvn organizer:set-version -DreleaseVersion -DupdateChildren -Dorganizer.dryRun
```

## Keeping POMs organized in CI

Bind the check to `validate` so an unorganized POM fails the build with a diff:

```xml
<plugin>
  <groupId>io.github.pgatzka</groupId>
  <artifactId>organizer-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <id>check-pom-is-organized</id>
      <goals>
        <goal>organize</goal>
      </goals>
      <configuration>
        <checkOnly>true</checkOnly>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The fix is one command:

```console
mvn organizer:organize
```

If you would rather not add the plugin to the POM, run it from the workflow instead:

```yaml
- name: The POM must be organized
  run: mvn -B io.github.pgatzka:organizer-maven-plugin:1.0.0:organize -DcheckOnly
```

## Changing many repositories at once

Because the goals need no project and never reformat, they compose with the usual shell tools.

```bash
for repo in ~/work/*/; do
  (cd "$repo" && mvn -B -q organizer:set-property \
      -Dproperty=maven.compiler.release -Dvalue=21)
done
```

Rehearse it first — `-Dorganizer.dryRun` turns the loop into a review:

```bash
for repo in ~/work/*/; do
  echo "== $repo"
  (cd "$repo" && mvn -B -q organizer:add-dependency \
      -Dartifact=org.slf4j:slf4j-api -Dorganizer.dryRun)
done
```

Every POM in a reactor, including the modules:

```bash
find . -name pom.xml -not -path '*/target/*' | while read -r pom; do
  mvn -B -q organizer:organize -Dorganizer.pom="$pom"
done
```

## Adding an internal repository

```console
mvn organizer:add-repository -Did=acme-internal \
    -Durl=https://nexus.acme.example/repository/maven-public \
    -Dname="Acme internal mirror"

mvn organizer:add-repository -Did=acme-internal \
    -Durl=https://nexus.acme.example/repository/maven-public \
    -DpluginRepository
```

The id is what `settings.xml` matches credentials against, so keep it the same in both.

## Working in a profile

`-Dprofile` sends any write goal into a profile. `-DcreateProfile` makes it first if needed.

```console
mvn organizer:add-profile -Dprofile=local -DactivationProperty=local
mvn organizer:add-dependency -Dartifact=com.h2database:h2 -Dprofile=local
mvn organizer:set-property -Dproperty=spring.profiles.active -Dvalue=local -Dprofile=local
```

Or in one step:

```console
mvn organizer:add-dependency -Dartifact=com.h2database:h2 -Dprofile=local -DcreateProfile
```

Read it back:

```console
mvn organizer:list-profiles
mvn organizer:list-dependencies -Dprofile=local
```

## Driving it from a script or an agent

Two rules make the goals safe to automate.

**Use `-B`.** Batch mode turns off every prompt. A goal missing a required parameter then fails
immediately with a message naming the parameter, instead of blocking on standard input:

```console
$ mvn -B organizer:add-dependency
[ERROR] Missing required parameter 'groupId'. Pass -DgroupId=<value>, or run without -B to be
        asked for it.
```

**Check the exit code, not the output.** Every goal fails the build when it cannot do what was
asked. The ones that might reasonably find nothing to do — the remove goals — report it and
succeed, unless you pass `-DfailIfMissing`.

A safe edit-and-verify loop:

```bash
set -euo pipefail

mvn -B organizer:add-dependency -Dartifact=org.slf4j:slf4j-api -Dorganizer.backup
if ! mvn -B -q verify; then
  mv pom.xml.bak pom.xml     # put it back exactly as it was
  exit 1
fi
rm -f pom.xml.bak
```

The backup is byte-identical to the original, so restoring it leaves no trace.
