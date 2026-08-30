def log = new File(basedir, 'build.log').text
assert log.contains('is not organized') : "check mode did not report the problem:\n$log"

def pom = new File(basedir, 'pom.xml').text
assert pom.indexOf('<artifactId>') < pom.indexOf('<modelVersion>') : 'check mode rewrote the POM'

// A post-build script must hand back true; any other non-null value is read as a failure.
return true
