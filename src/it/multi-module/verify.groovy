assert new File(basedir, 'pom.xml').text.contains('<version>2.0.0</version>')

['child-a', 'child-b'].each { child ->
    def pom = new File(basedir, "$child/pom.xml").text
    assert pom.contains('<version>2.0.0</version>') : "$child still points at the old parent version:\n$pom"
    assert pom.contains("<artifactId>$child</artifactId>")
}

// A post-build script must hand back true; any other non-null value is read as a failure.
return true
