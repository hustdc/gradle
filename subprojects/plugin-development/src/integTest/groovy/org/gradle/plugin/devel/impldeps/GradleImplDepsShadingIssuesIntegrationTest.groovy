/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue

class GradleImplDepsShadingIssuesIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    @Issue("GRADLE-3456")
    def "doesn't fail when using Ivy in a plugin"() {

        when:
        buildFile << testableGroovyProject()
        file('src/main/groovy/MyPlugin.groovy') << '''
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {

                void apply(Project project) {
                    def conf = project.configurations.create('bug')
                    project.repositories { jcenter() }
                    project.dependencies {
                        bug 'junit:junit:4.12'
                    }
                    conf.resolve()
                }
            }
        '''
        file('src/test/groovy/MyPluginTest.groovy') << """
            class MyPluginTest extends groovy.util.GroovyTestCase {

                void testCanUseProjectBuilder() {
                    def project = ${ProjectBuilder.name}.builder().build()
                    project.plugins.apply(MyPlugin)
                    project.evaluate()
                }
            }
        """

        then:
        succeeds 'test'
    }
}
