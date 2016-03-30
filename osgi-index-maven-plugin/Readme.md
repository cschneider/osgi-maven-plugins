# osgi-index-maven-plugin

Creates an index out of dependencies specified in the same pom.

## Differences to bnd-indexer-maven-plugin
The bnd-indexer plugin is meant to create an index and deploy it into maven repos for consumption by anyone. This approach has some issues as the created http based urls do not cope well with
maven snapshots. 

This plugin has a different scope. It is meant to be used inside an bndtools based maven build. It creates the index only in the target dir and only uses relative urls. This allows to cope 
very well with maven snapshot. So it covers the cases where the bnd-indexer-plugin currently is weak.

# Build 

mvn clean install

Usage

Add your needed bundles as dependencies to your pom. The plugin will use all depenencies of scope runtime+compile and also include transitive dependencies.
So use excludes to omit incorrect dependencies.

The plugin will copy all dependencies into the target dir and create an OSGi repository index with relative urls to the bundles.
The idea is to create this index inside a maven based bndtools application. 

```
<plugin>
    <groupId>net.lr</groupId>
    <artifactId>osgi-index-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
       <execution>
           <goals>
               <goal>index</goal>
           </goals>
       </execution>
    </executions>
</plugin>
```

