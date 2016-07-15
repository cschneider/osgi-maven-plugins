
# Build

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

