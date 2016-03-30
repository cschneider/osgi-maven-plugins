
# Build

Unfortunately we need two jars from bnd that are not exported to maven central. So we need to add them to our local repo by hand.

1. Checkout and build bnd
2. From the bnd main directory call the mvn commands below

```
mvn install:install-file -Dfile=biz.aQute.resolve/generated/biz.aQute.resolve-3.2.0.jar -DgroupId=biz.aQute.bnd -DartifactId=biz.aQute.resolve -Dversion=3.2.0-SNAPSHOT -Dpackaging=jar
mvn install:install-file -Dfile=biz.aQute.repository/generated/biz.aQute.repository-3.2.0.jar -DgroupId=biz.aQute.bnd -DartifactId=biz.aQute.repository -Dversion=3.2.0-SNAPSHOT -Dpackaging=jar
```

3. Now build this project

```
mvn clean install
```

# Usage

Point the plugin to a bndrun file in the same project. It will resolve the project and export
a runnable jar into the targetDir. If not specified targetDir defaults to the project build directory.

```
<plugin>
    <groupId>net.lr</groupId>
    <artifactId>bndrun-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <configuration>
      <bndrun>service.bndrun</bndrun>
      <targetDir>.</targetDir>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>resolve</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

