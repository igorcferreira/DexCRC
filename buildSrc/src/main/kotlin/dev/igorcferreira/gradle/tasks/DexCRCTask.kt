/*
 * Copyright (c) 2021 Igor Ferreira (https://igorcferreira.dev/)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package dev.igorcferreira.gradle.tasks

import dev.igorcferreira.gradle.domain.HashFunctions
import dev.igorcferreira.gradle.model.configuration.DexCRCConfiguration
import dev.igorcferreira.gradle.model.exceptions.InvalidInputException
import dev.igorcferreira.gradle.model.exceptions.OutdatedCRCFileException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * The DexCRCTask can be used to support the implementation of a safety check against code tempering.
 * One strategy to protect the APK from code tempering is (at runtime) compare the CRC of classes.dex files
 * with a value pre-populated into the resources. This way, if the CRC differs, the application can be
 * stopped since it may be victim of code tempering
 */
open class DexCRCTask: DefaultTask() {
    /**
     * {@link #dexPath} is the property used to inform the script where to look for the classes.dex files.
     * - For a single dex operation, this can be linked directly to the dex file, like "/path/to/classes.dex"
     * - For a multi-dex operation, this can be linked to the path that holds all the classes
     * */
    @Suppress("MemberVisibilityCanBePrivate")
    lateinit var dexPath: String

    /** {@link #outputPath} the a path to where the task will write is output. The outputs are as follow:
     * - Single dex operation: crc.txt
     * - Multi-dex operation: crc_classes.dev
     * */
    @Suppress("MemberVisibilityCanBePrivate")
    lateinit var outputPath: String

    /**
     * {@link #abortIfCRCNotUpdated} is a safety flag that can be enabled to prevent the release of an app
     * with an invalid crc information. If this value is true, the task will compare the recently calculated
     * crc with the old files and abort the build if they differ.
     *
     * Default: false
     * */
    @Suppress("MemberVisibilityCanBePrivate")
    var abortIfCRCNotUpdated: Boolean = false

    @Option(option = "disableAbort",
        description = "This argument disables the abort configuration. It can be used to better enforce dependencies")
    fun disableAbort() {
        abortIfCRCNotUpdated = false
    }

    @TaskAction
    fun extractCRC() {
        val configuration = validateInputs()
        if (configuration.dexFile.isDirectory) {
            calculateMultiple(configuration)
        } else {
            calculateSingle(configuration)
        }
    }

    private fun calculateSingle(configuration: DexCRCConfiguration) {
        val hash = HashFunctions.calculateCRC(configuration.dexFile)
        val oldContext = configuration.outputFile.readText()
        configuration.outputFile.writeText(hash)

        if (abortIfCRCNotUpdated && oldContext != hash) {
            throw OutdatedCRCFileException("${configuration.outputFile.name} was outdated." +
                    "Re-run the gradle command to use the latest version")
        }
    }

    private fun calculateMultiple(configuration: DexCRCConfiguration) {
        val oldTable = loadCRCTable(configuration.outputFile)

        val dexFiles = configuration.dexFile.listFiles { file ->
            file.name.matches(Regex("(classes)[0-9]*\\.dex"))
        }

        var oldTableIsInvalid = false

        val hashes = dexFiles?.map { file ->
            val hash = HashFunctions.calculateCRC(file)
            oldTableIsInvalid = oldTableIsInvalid || hash != oldTable[file.name]
            "${file.name};$hash"
        }
        hashes?.let {
            val lines = it.joinToString("\n")
            configuration.outputFile.writeText(lines)
        }
        if (abortIfCRCNotUpdated && oldTableIsInvalid) {
            throw OutdatedCRCFileException("${configuration.outputFile.name} was outdated." +
                    "Re-run the gradle command to use the latest version")
        }
    }

    private fun loadCRCTable(table: File): Map<String, String> {
        val resultMap = mutableMapOf<String, String>()
        table.readLines().onEach {
            val elements = it.split(";")
            if (elements.size == 2) {
                resultMap[elements.first()] = elements.last()
            }
        }
        return resultMap
    }

    private fun validateInputs(): DexCRCConfiguration {
        if (dexPath.isBlank()) {
            throw InvalidInputException("A path to a folder containing the dex files needs to be set using dexPath")
        }

        if (outputPath.isBlank()) {
            throw InvalidInputException("An output path needs to be set")
        }

        val dexFile = File(dexPath)
        if (!dexFile.exists()) {
            throw InvalidInputException("The dex path $dexPath do not exists")
        }
        if (!dexFile.isDirectory && dexFile.extension != "dex") {
            throw InvalidInputException("The file needs to be a dex file: $dexPath")
        }

        val outputPath = File(outputPath)

        if (outputPath.exists() && !outputPath.isDirectory) {
            throw InvalidInputException("The output needs to be a path. Passed $outputPath")
        }

        if (!outputPath.exists()) {
            outputPath.mkdir()
        }

        val outputFileName = if(dexFile.isDirectory) "crc_classes.csv" else "crc.txt"
        val outputFile = File(outputPath, outputFileName)
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        return DexCRCConfiguration(dexFile, outputFile)
    }
}