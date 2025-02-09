/*
 * Copyright ${year} the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle

import org.intellij.lang.annotations.Language
import java.io.File

/**
 * Utility to help with writing gradle projects to disk to assist with plugin testing
 */
class GradleProjectSpec(
    val dir: File
) {
    val subprojects: MutableList<GradleProjectSpec> = mutableListOf()
    val sourceSets: MutableList<GradleSourceSetSpec> = mutableListOf()

    @Language("groovy")
    var groovyBuildScript: String? = null

    fun buildGradle(@Language("groovy") text: String) {
        groovyBuildScript = text.trimIndent()
    }

    @Language("groovy")
    var settingsGradle: String? = null
    fun settingsGradle(@Language("groovy") text: String) {
        settingsGradle = text.trimIndent();
    }

    @Language("yaml")
    var rewriteYaml: String? = null
    fun rewriteYaml(@Language("yaml") text: String) {
        rewriteYaml = text.trimIndent()
    }

    @Language("xml")
    var checkstyleXml: String? = null
    fun checkstyleXml(@Language("xml") text: String) {
        checkstyleXml = text.trimIndent()
    }

    val propertiesFiles: MutableMap<String, String> = mutableMapOf()
    fun propertiesFile(name: String, @Language("properties") text: String) {
        propertiesFiles[name] = text
    }

    val textFiles: MutableMap<String, String> = mutableMapOf()
    fun textFile(name: String, text: String) {
        textFiles[name] = text
    }

    fun subproject(name: String, init: GradleProjectSpec.()->Unit): GradleProjectSpec {
        val subproject = GradleProjectSpec(File(dir, name)).apply(init)
        subprojects.add(subproject)
        return subproject
    }

    fun sourceSet(name: String, init: GradleSourceSetSpec.()->Unit): GradleSourceSetSpec {
        val sourceSet = GradleSourceSetSpec(name).apply(init)
        sourceSets.add(sourceSet)
        return sourceSet
    }

    fun build(): GradleProjectSpec {
        dir.mkdirs()

        if(settingsGradle != null) {
            File(dir, "settings.gradle").writeText(settingsGradle!!)
        }

        if(groovyBuildScript != null) {
            File(dir, "build.gradle").writeText(groovyBuildScript!!)
        }

        if(rewriteYaml != null) {
            File(dir, "rewrite.yml").writeText(rewriteYaml!!)
        }

        if(checkstyleXml != null) {
            File(dir, "config/checkstyle/checkstyle.xml").apply {
                parentFile.mkdirs()
                writeText(checkstyleXml!!)
            }
        }

        for(props in propertiesFiles.entries) {
            File(dir, props.key).apply {
                parentFile.mkdirs()
                writeText(props.value)
            }
        }

        for(text in textFiles.entries) {
            File(dir, text.key).apply {
                parentFile.mkdirs()
                writeText(text.value)
            }
        }

        for(sourceSet in sourceSets) {
            sourceSet.build(File(dir, "src"))
        }

        val settings = File(dir, "settings.gradle")
        val settingsText = "rootProject.name = \"${dir.name}\"\n"
        if(subprojects.isEmpty()) {
            settings.writeText("rootProject.name = \"${dir.name}\"\n")
        } else {
            val subprojectsDeclarations = subprojects.joinToString("\n") { subproject -> "include('${subproject.dir.name}')" }
            settings.writeText(settingsText + subprojectsDeclarations)
            for(subproject in subprojects) {
                subproject.build()
            }
        }
        return this
    }
}

class GradleSourceSetSpec(
    val name: String
) {
    val javaSources: MutableList<String> = mutableListOf()
    fun java(@Language("java") source: String) {
        javaSources.add(source.trimIndent())
    }

    val kotlinSources: MutableList<String> = mutableListOf()
    fun kotlin(@Language("kotlin") source: String) {
        kotlinSources.add(source.trimIndent())
    }

    val propertiesFiles: MutableMap<String, String> = mutableMapOf()
    fun propertiesFile(name: String, @Language("properties") text: String) {
        propertiesFiles[name] = text
    }

    val yamlFiles: MutableMap<String, String> = mutableMapOf()
    fun yamlFile(name: String, @Language("yaml") text: String) {
        yamlFiles[name] = text
    }

    val groovyClassses: MutableList<String> = mutableListOf()
    fun groovyClass(@Language("groovy") source: String) {
        groovyClassses.add(source.trimIndent())
    }


    @Suppress("RegExpSimplifiable")
    fun build(dir: File): GradleSourceSetSpec {
        dir.mkdirs()
        for(javaSource in javaSources) {
            val peckage = if(javaSource.startsWith("package")) {
                "package\\s+([a-zA-Z0-9.]+);".toRegex(RegexOption.MULTILINE)
                    .find(javaSource)!!
                    .groupValues[1]
            } else {
                ""
            }.replace(".", "/")
            val clazz = ".*(class|interface|enum)\\s+([a-zA-Z0-9-_]+)".toRegex(RegexOption.MULTILINE).find(javaSource)!!.groupValues[2]
            val path = if(peckage.isEmpty()) {
                "$name/java/$clazz.java"
            } else {
                "$name/java/$peckage/$clazz.java"
            }
            File(dir, path).apply {
                parentFile.mkdirs()
                writeText(javaSource)
            }
        }
        for(kotlinSource in kotlinSources) {
            val peckage = if(kotlinSource.startsWith("package")) {
                "package\\s+([a-zA-Z0-9.]+)".toRegex(RegexOption.MULTILINE)
                    .find(kotlinSource)!!
                    .groupValues[1]
            } else {
                ""
            }.replace(".", "/")
            val clazz = ".*(class|interface|enum)\\s+([a-zA-Z0-9-_]+)".toRegex(RegexOption.MULTILINE).find(kotlinSource)!!.groupValues[2]
            val path = if(peckage.isEmpty()) {
                "$name/kotlin/$clazz.kt"
            } else {
                "$name/kotlin/$peckage/$clazz.kt"
            }
            File(dir, path).apply {
                parentFile.mkdirs()
                writeText(kotlinSource)
            }
        }
        for(groovySource in groovyClassses) {
            val peckage = if(groovySource.startsWith("package")) {
                "package\\s+([a-zA-Z0-9.]+);?".toRegex(RegexOption.MULTILINE)
                    .find(groovySource)!!
                    .groupValues[1]
            } else {
                ""
            }.replace(".", "/")
            val clazz = ".*(class|interface|enum)\\s+([a-zA-Z0-9-_]+)".toRegex(RegexOption.MULTILINE).find(groovySource)!!.groupValues[2]
            val path = if(peckage.isEmpty()) {
                "$name/groovy/$clazz.groovy"
            } else {
                "$name/groovy/$peckage/$clazz.groovy"
            }
            File(dir, path).apply {
                parentFile.mkdirs()
                writeText(groovySource)
            }
        }
        if(propertiesFiles.isNotEmpty()) {
            for(props in propertiesFiles.entries) {
                File(dir, "$name/resources/${props.key}").apply {
                    parentFile.mkdirs()
                    writeText(props.value)
                }
            }
        }
        if(yamlFiles.isNotEmpty()) {
            for(yaml in yamlFiles.entries) {
                File(dir, "$name/resources/${yaml.key}").apply {
                    parentFile.mkdirs()
                    writeText(yaml.value)
                }
            }
        }
        return this
    }
}

fun gradleProject(dir: File, init: GradleProjectSpec.()->Unit): GradleProjectSpec {
    return GradleProjectSpec(dir).apply(init).build()
}
