package io.jafar.gradle

import io.jafar.utils.TypeGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

class TypeGeneratorPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create('generateSources', GenerateSourcesExtension, project)

        project.afterEvaluate {
            if (project.hasProperty('jafar.input')) {
                extension.inputFile.set(project.file(project.property('jafar.input')))
            }
        }

        // Define the directory for generated sources
        def generatedSourcesDir = project.file("${project.buildDir}/generated/sources/jafar/src/main")

        // Register a task for generating sources
        project.tasks.register('generateJafarTypes') {
            group = "build"
            description = "Generate Jafar type sources into the 'generated/sources' folder"

            // Define inputs and outputs for task up-to-date checks
            inputs.file(extension.inputFile).optional(true)
            outputs.dir(generatedSourcesDir)

            doLast {
                if (!extension.inputFile.isPresent()) {
                    println "No input file provided. Using runtime provided JFR type definitions."
                } else {
                    println "Running TypeGenerator with input file ${extension.inputFile.get()} to generate sources into ${generatedSourcesDir}"
                }

                def input = extension.inputFile.isPresent() ? extension.inputFile.get() : null
                def output = generatedSourcesDir

                // Ensure output directory exists
                output.mkdirs()

                // Instantiate and execute TypeGenerator
                def generator = new TypeGenerator(input?.toPath(), output.toPath())
                generator.generate()
            }
        }

        // Add generated sources to the main source set
        project.afterEvaluate {
            project.sourceSets.main.java.srcDir(generatedSourcesDir)
            project.tasks.named('compileJava').configure {
                dependsOn 'generateJafarTypes'
            }
        }
    }

    // Extension to configure the input file
    static class GenerateSourcesExtension {
        final Property<File> inputFile

        GenerateSourcesExtension(Project project) {
            inputFile = project.objects.property(File)
        }
    }
}