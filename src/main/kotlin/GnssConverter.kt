import java.io.File
import kotlin.math.round

enum class Constellation(val typeId: Int, val letter: Char) {
    UNKNOWN(0, 'X'),
    GPS(1, 'G'),
    SBAS(2, 'S'),
    GLONASS(3, 'R'),
    QZSS(4, 'J'),
    BEIDOU(5, 'C'),
    GALILEO(6, 'E');

    companion object {
        fun fromId(id: Int): Constellation {
            return values().find { it.typeId == id } ?: UNKNOWN
        }
    }
}


// Flags to check wether the measurement is correct or not
// https://developer.android.com/reference/android/location/GnssMeasurement.html#getState()

object GnssState {
    const val UNKNOWN = 0x00000000
    const val CODE_LOCK = 0x00000001
    const val BIT_SYNC = 0x00000002
    const val SUBFRAME_SYNC = 0x00000004
    const val TOW_DECODED = 0x00000008
    const val MSEC_AMBIGUOUS = 0x00000010
    const val SYMBOL_SYNC = 0x00000020
    const val GLO_STRING_SYNC = 0x00000040
    const val GLO_TOD_DECODED = 0x00000080
    const val BDS_D2_BIT_SYNC = 0x00000100
    const val BDS_D2_SUBFRAME_SYNC = 0x00000200
    const val GAL_E1BC_CODE_LOCK = 0x00000400
    const val GAL_E1C_2ND_CODE_LOCK = 0x00000800
    const val GAL_E1B_PAGE_SYNC = 0x00001000
    const val SBAS_SYNC = 0x00002000
    const val TOW_KNOWN = 0x00004000
    const val GLO_TOD_KNOWN = 0x00008000
    const val SECOND_CODE_LOCK = 0x00010000
}

object AdrState {
    const val UNKNOWN = 0x00000000
    const val VALID = 0x00000001
    const val RESET = 0x00000002
    const val CYCLE_SLIP = 0x00000004
    const val HALF_CYCLE_RESOLVED = 0x00000008
    const val HALF_CYCLE_REPORTED = 0x00000010
}

fun Int.hasState(flag: Int): Boolean = (this and flag) != 0


data class RawMeasurement(
    val constellation: Constellation,
    val svid: Int,
    val state: Int,
    val accumulatedDeltaRangeState: Int,
    val timeNanos: Double,
    val fullBiasNanos: Double,
    val biasNanos: Double,
    val timeOffsetNanos: Double,
    val receivedSvTimeNanos: Double,
    val pseudorangeRateMetersPerSecond: Double,
    val accumulatedDeltaRangeMeters: Double,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double
) {
    // Return satellite name in RINEX standard
    val satName: String
        get() = "${constellation.letter}${svid.toString().padStart(2, '0')}"
}


class GnssConverter {

    fun convert(
        inputFile: File,
        outputFile: File,
        integerize: Boolean = false,
        useFixedFullBias: Boolean = false
    ) {
        val parser = GnssLogParser()
        val processor = MeasurementProcessor(integerize, useFixedFullBias)
        val formatter = RinexFormatter()

        // Start file parser
        parser.parseMeasurements(inputFile) { rawSequence ->
            val rawList = rawSequence.toList()
            if (rawList.isEmpty()) return@parseMeasurements

            val obsList = extractGlobalObsList(rawList)
            val gloSlotFreqChns = extractGloFreqChnList(rawList)

            // Process raw data
            val epochSequence = processor.process(rawList.asSequence())
            val batches = epochSequence.toList()

            val firstEpoch = batches.first().epochTime
            val lastEpoch = batches.last().epochTime

            // Save data with RINEX formatting
            outputFile.bufferedWriter().use { writer ->
                formatter.writeHeader(writer, firstEpoch, lastEpoch, obsList, gloSlotFreqChns)
                batches.forEach { batch ->
                    formatter.writeEpoch(writer, batch, obsList)
                }
            }
        }
    }

    /**
     * Scan all epochs and collect unique measurements types for each constellation.
     * Sort codes according to RINEX standard (C, L, D, S).
     */
    private fun extractGlobalObsList(rawList: List<RawMeasurement>): Map<Char, List<String>> {
        val rawObsMap = mutableMapOf<Char, MutableSet<String>>()
        for (meas in rawList) {
            val sysLetter = meas.satName[0]
            val obsCode = ObservableResolver.getObsCode(meas)
            rawObsMap.getOrPut(sysLetter) { mutableSetOf() }.add(obsCode)
        }
        val orderedPrefixes = listOf("C", "L", "D", "S")

        return rawObsMap.toSortedMap().mapValues { (_, cores) ->
            cores.sorted().flatMap { core ->
                orderedPrefixes.map { prefix -> "$prefix$core" }
            }
        }
    }

    private fun extractGloFreqChnList(rawList: List<RawMeasurement>): Map<String, Int> {
        val freqChnList = mutableMapOf<String, Int>()
        val gloL1CenterFreq = 1.60200e9
        val gloL1Dfreq = 0.56250e6

        for (meas in rawList) {
            if (meas.constellation == Constellation.GLONASS) {
                if (meas.svid > 50) continue

                val freq = meas.carrierFrequencyHz ?: (154 * 10.23e6)
                val freqChn = kotlin.math.round((freq - gloL1CenterFreq) / gloL1Dfreq).toInt()
                freqChnList[meas.satName] = freqChn
            }
        }
        return freqChnList
    }
}