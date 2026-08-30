def pom = new File(basedir, 'pom.xml').text
assert !pom.contains('widget') : 'a dry run wrote to the POM'
assert !new File(basedir, 'pom.xml.bak').exists() : 'a dry run wrote a backup'

def log = new File(basedir, 'build.log').text
assert log.contains('Dry run, not writing') : 'the dry run was not announced'
assert log.contains('+      <artifactId>widget</artifactId>') : "no diff in the log:\n$log"

// A post-build script must hand back true; any other non-null value is read as a failure.
return true
