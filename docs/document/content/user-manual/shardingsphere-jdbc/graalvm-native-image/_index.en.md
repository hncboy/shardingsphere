+++
title = "GraalVM Native Image"
weight = 8
chapter = true
+++

## Background Information

ShardingSphere JDBC has been validated for availability under GraalVM Native Image.

Build GraalVM Native containing Maven dependencies of `org.apache.shardingsphere:shardingsphere-jdbc-core:${shardingsphere.version}`
Image, you need to resort to GraalVM Native Build Tools. GraalVM Native Build Tools provides Maven Plugin and Gradle Plugin 
to simplify long list of shell commands for GraalVM CE's `native-image` tool.

ShardingSphere JDBC requires GraalVM Native Image to be built with GraalVM CE as follows or higher. Users can quickly switch 
JDK through `SDKMAN!`. Same reason applicable to downstream distributions of `GraalVM CE` such as `Oracle GraalVM`, `Liberica Native Image Kit` 
and `Mandrel`.

- GraalVM CE 23.0.2 For JDK 17.0.9, corresponding to `17.0.9-graalce` of SDKMAN!
- GraalVM CE 23.0.2 For JDK 21.0.1, corresponding to `21.0.1-graalce` of SDKMAN!

### Maven Ecology

Users need to configure additional `BuildArgs` to prevent GroovyShell related classes from reporting errors when building 
GraalVM Native Image. and proactively use GraalVM Reachability Metadata central repository. The following configuration 
is for reference to configure additional Maven Profiles for the project, and the documentation of GraalVM Native Build 
Tools shall prevail.

```xml
<project>
     <dependencies>
         <dependency>
             <groupId>org.apache.shardingsphere</groupId>
             <artifactId>shardingsphere-jdbc-core</artifactId>
             <version>${shardingsphere.version}</version>
         </dependency>
     </dependencies>
    
     <build>
         <plugins>
             <plugin>
                 <groupId>org.graalvm.buildtools</groupId>
                 <artifactId>native-maven-plugin</artifactId>
                 <version>0.9.28</version>
                 <extensions>true</extensions>
                 <configuration>
                     <buildArgs>
                         <arg>--report-unsupported-elements-at-runtime</arg>
                     </buildArgs>
                     <metadataRepository>
                         <enabled>true</enabled>
                     </metadataRepository>
                 </configuration>
                 <executions>
                     <execution>
                         <id>build-native</id>
                         <goals>
                             <goal>compile-no-fork</goal>
                         </goals>
                         <phase>package</phase>
                     </execution>
                     <execution>
                         <id>test-native</id>
                         <goals>
                             <goal>test</goal>
                         </goals>
                         <phase>test</phase>
                     </execution>
                 </executions>
             </plugin>
         </plugins>
     </build>
</project>
```

### Gradle Ecosystem

Users need to configure additional `BuildArgs` to prevent GroovyShell related classes from reporting errors when building
GraalVM Native Image. and proactively use GraalVM Reachability Metadata central repository. The following configuration
is for reference to configure additional Gradle Task for the project, and the documentation of GraalVM Native Build
Tools shall prevail.

```groovy
plugins {
     id 'org.graalvm.buildtools.native' version '0.9.28'
}

dependencies {
     implementation 'org.apache.shardingsphere:shardingsphere-jdbc-core:${shardingsphere.version}'
}

graalvmNative {
     binaries {
         main {
             buildArgs.add('--report-unsupported-elements-at-runtime')
         }
         test {
             buildArgs.add('--report-unsupported-elements-at-runtime')
         }
     }
     metadataRepository {
         enabled = true
     }
}
```

### For build tools such as SBT that are not supported by GraalVM Native Build Tools

Such requirements require opening additional issues at https://github.com/graalvm/native-build-tools and providing the Plugin 
implementation of the corresponding build tool.


### Usage restrictions

1. The following algorithm classes are not available under GraalVM Native Image due to the involvement of https://github.com/oracle/graal/issues/5522.
    - `org.apache.shardingsphere.sharding.algorithm.sharding.inline.InlineShardingAlgorithm`
    - `org.apache.shardingsphere.sharding.algorithm.sharding.inline.ComplexInlineShardingAlgorithm`
    - `org.apache.shardingsphere.sharding.algorithm.sharding.hint.HintInlineShardingAlgorithm`

For general cases, users can simulate the behavior of GroovyShell by themselves through the `CLASS_BASE` algorithm. For example, 
take the following configuration.

```yaml
rules:
- !SHARDING
   defaultDatabaseStrategy:
       standard:
         shardingColumn: user_id
         shardingAlgorithmName: inline
   shardingAlgorithms:
     inline:
       type: INLINE
       props:
         algorithm-expression: ds_${user_id % 2}
         allow-range-query-with-inline-sharding: false
```

You can first define the implementation class of `CLASS_BASE`.

```java
package org.example.test;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;

public final class TestShardingAlgorithmFixture implements StandardShardingAlgorithm<Integer> {
    
    @Override
    public String doSharding(final Collection<String> availableTargetNames, final PreciseShardingValue<Integer> shardingValue) {
        String resultDatabaseName = "ds_" + shardingValue.getValue() % 2;
        for (String each : availableTargetNames) {
            if (each.equals(resultDatabaseName)) {
                return each;
            }
        }
        return null;
    }
    
    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames, final RangeShardingValue<Integer> shardingValue) {
        throw new RuntimeException("This algorithm class does not support range queries.");
    }
}
```

Modify the relevant YAML configuration as follows.

```yaml
rules:
- !SHARDING
   defaultDatabaseStrategy:
       standard:
         shardingColumn: user_id
         shardingAlgorithmName: inline
   shardingAlgorithms:
     inline:
         type: CLASS_BASED
         props:
           strategy: STANDARD
           algorithmClassName: org.example.test.TestShardingAlgorithmFixture
```

Add the following content to `src/main/resources/META-INF/native-image/exmaple-test-metadata/reflect-config.json` to used 
normally under GraalVM Native Image.

```json
[
{
   "name":"org.example.test.TestShardingAlgorithmFixture",
   "methods":[{"name":"<init>","parameterTypes":[] }]
}
]
```

2. For the `ReadWrite Splitting` feature, you need to use other implementations of `Row Value Expressions` SPI to configure 
`logic database name`, `writeDataSourceName` and `readDataSourceNames` when bypassing calls to GroovyShell. One possible 
configuration is to use the `Row Value Expressions` SPI implementation of `LITERAL`. The same applies to `actualDataNodes` 
for the `Sharding` feature.

```yaml
rules:
- !READWRITE_SPLITTING
   dataSources:
     <LITERAL>readwrite_ds:
       writeDataSourceName: <LITERAL>ds_0
       readDataSourceNames:
         - <LITERAL>ds_1
         - <LITERAL>ds_2
```

3. Users still need to configure GraalVM Reachability Metadata for independent files in the `src/main/resources/META-INF/native-image` 
folder or `src/test/resources/META-INF/native-image` folder. Users can quickly collect GraalVM Reachability Metadata through 
the GraalVM Tracing Agent of GraalVM Native Build Tools. 

4. DistSQL availability has not been verified. Users need to add additional GraalVM Reachability Metadata by themselves.

## Contribute GraalVM Reachability Metadata

The verification of ShardingSphere's availability under GraalVM Native Image is completed through the Maven Plugin subproject 
of GraalVM Native Build Tools. By running the unit test under the JVM, label the unit test with `junit-platform-unique-ids*`, 
and then build it as GraalVM Native Image for nativeTest to test Unit Test Coverage under GraalVM Native Image. 
Please do not use `io.kotest:kotest-runner-junit5-jvm:5.5.4` and some third-party test libraries, they are in `test listener` 
mode failed to discover tests.

ShardingSphere defines the Maven Module of `shardingsphere-infra-nativetest` to provide a small subset of unit tests for native Test.
This subset of unit tests avoids the use of third-party libraries such as Mockito that are not available under native Test.

ShardingSphere defines the Maven Profile of `nativeTestInShardingSphere` for executing nativeTest for the `shardingsphere-infra-nativetest` module.

Assuming that the contributor is under a new Ubuntu 22.04.3 LTS instance, Contributors can manage the JDK and tool chain through 
`SDKMAN!` through the following bash command, and execute nativeTest for the `shardingsphere-infra-nativetest` submodule.

```bash
sudo apt install unzip zip curl sed -y
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 17.0.9-graalce
sdk use java 17.0.9-graalce
sudo apt-get install build-essential libz-dev zlib1g-dev -y

git clone git@github.com:apache/shardingsphere.git
cd ./shardingsphere/
./mvnw -PnativeTestInShardingSphere -T1C -e clean test
```

When contributors discover that GraalVM Reachability Metadata is missing for a third-party library not related to ShardingSphere, 
they should open a new issue and submit PR containing the missing third-party library GraalVM Reachability Metadata that depends 
on https://github.com/oracle/graalvm-reachability-metadata . ShardingSphere actively hosts the GraalVM Reachability Metadata of 
some third-party libraries in the `shardingsphere-infra-reachability-metadata` submodule.

If nativeTest execution fails, preliminary GraalVM Reachability Metadata should be generated for unit tests and manually 
adjusted to fix nativeTest. If necessary, use the `org.junit.jupiter.api.condition.DisabledInNativeImage` annotation or the 
`org.graalvm.nativeimage.imagecode` System Property blocks some unit tests from running under GraalVM Native Image.

ShardingSphere defines the Maven Profile of `generateMetadata`, which is used to carry GraalVM Reachability Metadata. Bring 
GraalVM Tracing Agent under GraalVM JIT Compiler to perform unit testing, and generate or merge existing GraalVM Reachability 
Metadata files in a specific directory.
This process can be easily handled with the following bash command. Contributors may still need to manually adjust specific 
JSON entries and GraalVM Tracing Agent Filter chain of Maven Profile.

The following command is only an example of using `shardingsphere-infra-nativetest` to generate GraalVM Reachability Metadata 
in Conditional form. Generated GraalVM Reachability Metadata is located under the `shardingsphere-infra-reachability-metadata` submodule.

For GraalVM Reachability Metadata used independently by test classes and test files, contributors should place
`${user.dir}/infra/nativetest/src/test/resources/META-INF/native-image/shardingsphere-infra-nativetest-test-metadata/`
folder. `${}` contains the regular system variables of POM 4.0 corresponding to the relevant submodules, which can be replaced by yourself.

```bash
git clone git@github.com:apache/shardingsphere.git
cd ./shardingsphere/
./mvnw -PgenerateMetadata -DskipNativeTests -e -T1C clean test native:metadata-copy
```

Please manually delete JSON files without any specific entries.
