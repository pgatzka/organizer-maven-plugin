def pom = new File(basedir, 'pom.xml').text

assert pom.contains('''  <dependencies>
    <dependency>
      <groupId>org.apache.commons</groupId>
      <artifactId>commons-lang3</artifactId>
      <version>3.14.0</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>''') : "dependencies were not added and organized:\n$pom"

assert pom.startsWith('<?xml version="1.0" encoding="UTF-8"?>') : 'the XML declaration was lost'

// A post-build script must hand back true; any other non-null value is read as a failure.
return true
