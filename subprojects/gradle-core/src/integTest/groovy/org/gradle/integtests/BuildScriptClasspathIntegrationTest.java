/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests;

import org.gradle.integtests.fixtures.ArtifactBuilder;
import org.gradle.util.TestFile;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class BuildScriptClasspathIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void providesADefaultBuildForBuildSrcProject() {
        testFile("buildSrc/src/main/java/BuildClass.java").writelns("public class BuildClass { }");
        testFile("build.gradle").writelns("new BuildClass()");
        inTestDirectory().withTaskList().run();
    }

    @Test
    public void cachesJarGeneratedByBuildSrc() {
        testFile("buildSrc/src/main/java/BuildClass.java").writelns("public class BuildClass { }");
        testFile("build.gradle").writelns(
                "new BuildClass()",
                "task test"
        );

        inTestDirectory().withTasks("test").run();

        TestFile buildSrcJar = testFile("buildSrc/build/libs/buildSrc.jar");
        buildSrcJar.assertIsFile();
        TestFile.Snapshot snapshot = buildSrcJar.snapshot();

        inTestDirectory().withTasks("test").run();

        buildSrcJar.assertHasNotChangedSince(snapshot);

        inTestDirectory().withArguments("-Crebuild").withTasks("test").run();

        buildSrcJar.assertHasChangedSince(snapshot);
        snapshot = buildSrcJar.snapshot();

        TestFile cacheProps = testFile("buildSrc/.gradle/noVersion/buildSrc/cache.properties");
        Map<String, String> properties = cacheProps.getProperties();
        properties.put("gradle.version", "some-other-version");
        cacheProps.writeProperties(properties);

        inTestDirectory().withTasks("test").run();
        buildSrcJar.assertHasChangedSince(snapshot);
    }

    @Test
    public void canDeclareClasspathInBuildScript() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile("org/gradle/test/ImportedClass.java").writelns(
                "package org.gradle.test;",
                "public class ImportedClass { }"
        );
        builder.sourceFile("org/gradle/test/StaticImportedClass.java").writelns(
                "package org.gradle.test;",
                "public class StaticImportedClass { public static int someValue = 12; }"
        );
        builder.sourceFile("org/gradle/test/StaticImportedFieldClass.java").writelns(
                "package org.gradle.test;",
                "public class StaticImportedFieldClass { public static int anotherValue = 4; }"
        );
        builder.sourceFile("org/gradle/test2/OnDemandImportedClass.java").writelns(
                "package org.gradle.test2;",
                "public class OnDemandImportedClass { }"
        );
        builder.buildJar(testFile("repo/test-1.3.jar"));

        testFile("build.gradle").writelns(
                "import org.gradle.test.ImportedClass",
                "import static org.gradle.test.StaticImportedClass.*",
                "import static org.gradle.test.StaticImportedFieldClass.anotherValue",
                "import org.gradle.test2.*",
                "buildscript {",
                "  repositories {",
                "    flatDir dirs: file('repo')",
                "  }",
                "  dependencies {",
                "    classpath name: 'test', version: '1.+'",
                "  }",
                "}",
                "task hello << {",
                "  new org.gradle.test.ImportedClass()",
                "  println someValue",
                "  println anotherValue",
                "  new ImportedClass()",
                "  new OnDemandImportedClass()",
                "}",
                "a = new ImportedClass()",
                "b = OnDemandImportedClass",
                "c = someValue",
                "d = anotherValue",
                "class TestClass extends ImportedClass { }",
                "def aMethod() { return new OnDemandImportedClass() }"
        );
        inTestDirectory().withTasks("hello").run();
    }

    @Test
    public void canUseBuildSrcAndSystemClassesInClasspathDeclaration() {
        testFile("buildSrc/src/main/java/org/gradle/buildsrc/test/ImportedClass.java").writelns(
                "package org.gradle.buildsrc.test;",
                "public class ImportedClass { }"
        );
        testFile("buildSrc/src/main/java/org/gradle/buildsrc/test/StaticImportedClass.java").writelns(
                "package org.gradle.buildsrc.test;",
                "public class StaticImportedClass { public static int someValue = 12; }"
        );
        testFile("buildSrc/src/main/java/org/gradle/buildsrc/test/StaticImportedFieldClass.java").writelns(
                "package org.gradle.buildsrc.test;",
                "public class StaticImportedFieldClass { public static int anotherValue = 4; }"
        );
        testFile("buildSrc/src/main/java/org/gradle/buildsrc/test2/OnDemandImportedClass.java").writelns(
                "package org.gradle.buildsrc.test2;",
                "public class OnDemandImportedClass { }"
        );

        testFile("build.gradle").writelns(
                "import org.gradle.buildsrc.test.ImportedClass",
                "import org.gradle.buildsrc.test2.*",
                "import static org.gradle.buildsrc.test.StaticImportedClass.*",
                "import static org.gradle.buildsrc.test.StaticImportedFieldClass.anotherValue",
                "buildscript {",
                "    new ImportedClass()",
                "    new org.gradle.buildsrc.test.ImportedClass()",
                "    new org.gradle.buildsrc.test2.OnDemandImportedClass()",
                "    println someValue",
                "    println anotherValue",
                "    List l = new ArrayList()",
                "    Project p = project",
                "    Closure cl = { }",
                "}",
                "task hello"
        );
        inTestDirectory().withTasks("hello").run();
    }

    @Test
    public void inheritsClassPathOfParentProject() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile("org/gradle/test/BuildClass.java").writelns(
                "package org.gradle.test;",
                "public class BuildClass { }"
        );
        builder.buildJar(testFile("repo/test-1.3.jar"));
        testFile("settings.gradle").writelns(
                "include 'child'"
        );
        testFile("build.gradle").writelns(
                "assert gradle.scriptClassLoader == buildscript.classLoader.parent",
                "buildscript {",
                "    repositories { flatDir(dirs: file('repo')) }",
                "    dependencies { classpath name: 'test', version: '1.3' }",
                "}"
        );
        testFile("child/build.gradle").writelns(
                "assert parent.buildscript.classLoader == buildscript.classLoader.parent",
                "task hello << ",
                "{",
                "    new org.gradle.test.BuildClass()",
                "}"
        );
        inTestDirectory().withTasks("hello").run();
    }

    @Test @Ignore
    public void reportsFailureDuringClasspathDeclaration() {
        fail("implement me");
    }

    @Test @Ignore
    public void canInjectClassPathIntoSubProjects() {
        fail("implement me");
    }
    
    @Test @Ignore
    public void canReuseClassPathRepositories() {
        fail("implement me");
    }
}
