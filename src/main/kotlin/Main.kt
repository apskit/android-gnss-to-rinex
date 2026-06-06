package com.gnss

import GnssConverter
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = ArgParser("gnsslogger-to-rnx")

    // Flags
    val input by parser.argument(ArgType.String, description = "Input flie with Android GNSS logs (.txt)")
    val output by parser.option(ArgType.String, shortName = "o", fullName = "output", description = "Path to RINEX output file").required()

    val navOutput by parser.option(
        ArgType.String,
        shortName = "n",
        fullName = "nav",
        description = "Path to output navigation RINEX file (.n)"
    )

    val integerize by parser.option(
        ArgType.Boolean,
        shortName = "i",
        fullName = "integerize",
        description = "Align measurements to the nearest second (uses PseudorangeRate)"
    ).default(false)

    val fixedBias by parser.option(
        ArgType.Boolean,
        shortName = "b",
        fullName = "fixedBias",
        description = "Maintains a constant FullBiasNanos value (prevents clock spikes)"
    ).default(false)

    parser.parse(args)

    val inputFile = File(input)
    val outputFile = File(output)

    val navFile = navOutput?.let { File(it) }

    if (!inputFile.exists()) {
        System.err.println("ERROR: Input file '${inputFile.absolutePath}' does not exist!")
        exitProcess(1)
    }

    println("=========================================")
    println(" Android GNSS Logger to RINEX Converter  ")
    println("=========================================")
    println("Input file : ${inputFile.name}")
    println("Output file : ${outputFile.name}")
    if (navFile != null) {
        println("RINEX Navigation file : ${navFile.name}")
    }
    println("Integerize (-i): $integerize")
    println("Fixed Bias (-b): $fixedBias")
    println("-----------------------------------------")

    val converter = GnssConverter()

    try {
        val startTime = System.currentTimeMillis()

        converter.convert(inputFile, outputFile, navFile, integerize, fixedBias)

        val duration = System.currentTimeMillis() - startTime
        println("Conversion ended in ${duration}ms.")
        println("RINEX file saved in: ${outputFile.absolutePath}")
        if (navFile != null) {
            println("RINEX Navigation file saved in: ${navFile.absolutePath}")
        }
    } catch (e: Exception) {
        System.err.println("CONVERSION ERROR:")
        e.printStackTrace()
        exitProcess(1)
    }
}