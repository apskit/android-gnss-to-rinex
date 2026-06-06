import java.io.File

class GnssLogParser {

    /**
     * Read log file lazily line by line.
     * Store list in action block.
     */
    fun parseMeasurements(file: File, action: (Sequence<RawMeasurement>) -> Unit) {
        file.useLines { lines ->
            val iterator = lines.iterator()
            var headerMap = emptyMap<String, Int>()

            // 1. Find header and "Raw" column definition
            while (iterator.hasNext()) {
                val line = iterator.next()
                if (line.startsWith("# Raw,")) {
                    val columns = line.removePrefix("# ").split(",")
                    // Create map: "Column Name" -> Index
                    headerMap = columns.mapIndexed { index, name -> name.trim() to index }.toMap()
                    break
                }
            }

            require(headerMap.isNotEmpty()) { "No '# Raw,...' header in logs file!" }

            // 2. Create sequence from lines
            val measurementsSequence = iterator.asSequence()
                .filter { it.startsWith("Raw,") }
                .mapNotNull { line -> parseRawLine(line, headerMap) }

            // 3. Run callback with the parsed data
            action(measurementsSequence)
        }
    }

    /**
     * Extract data from a string based on dynamically assigned column indexes from the header.
     */
    private fun parseRawLine(line: String, headerMap: Map<String, Int>): RawMeasurement? {
        val fields = line.split(",")

        fun getDouble(columnName: String, default: Double? = null): Double? {
            val index = headerMap[columnName] ?: return default
            if (index >= fields.size) return default
            return fields[index].trim().toDoubleOrNull() ?: default
        }

        fun getInt(columnName: String, default: Int? = null): Int? {
            val index = headerMap[columnName] ?: return default
            if (index >= fields.size) return default
            return fields[index].trim().toIntOrNull() ?: default
        }

        try {
            val constellationType = getInt("ConstellationType") ?: return null
            val svid = getInt("Svid") ?: return null

            // TODO: SYS fix
            //
            // Skip GLONASS satellites with invalid id (Pth: get_satname)
            if (constellationType == Constellation.GLONASS.typeId && svid > 50) return null

            return RawMeasurement(
                constellation = Constellation.fromId(constellationType),
                svid = svid,
                state = getInt("State", 0)!!,
                accumulatedDeltaRangeState = getInt("AccumulatedDeltaRangeState", 0)!!,
                timeNanos = getDouble("TimeNanos") ?: return null,
                fullBiasNanos = getDouble("FullBiasNanos") ?: return null,
                biasNanos = getDouble("BiasNanos", 0.0)!!,
                timeOffsetNanos = getDouble("TimeOffsetNanos", 0.0)!!,
                receivedSvTimeNanos = getDouble("ReceivedSvTimeNanos") ?: return null,
                pseudorangeRateMetersPerSecond = getDouble("PseudorangeRateMetersPerSecond") ?: return null,
                accumulatedDeltaRangeMeters = getDouble("AccumulatedDeltaRangeMeters") ?: return null,
                carrierFrequencyHz = getDouble("CarrierFrequencyHz"),
                cn0DbHz = getDouble("Cn0DbHz") ?: return null
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Scan through a file to analyze navigation logs
     * Returns a map of counted messages, broken down into constellation and signal type.
     */
    fun getNavStatistics(file: File): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()

        file.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("Nav,")) {
                    val parts = line.split(",")
                    if (parts.size >= 3) {
                        val typeId = parts[2].trim().toIntOrNull() ?: continue

                        val sysName = when (typeId) {
                            257 -> "GPS (L1 C/A)"       // 0x0101
                            258 -> "GPS (L2-CNAV)"      // 0x0102
                            769 -> "GLONASS (L1 C/A)"   // 0x0301
                            1281 -> "BEIDOU (D1)"       // 0x0501
                            1282 -> "BEIDOU (D2)"       // 0x0502
                            1537 -> "GALILEO (I/NAV)"   // 0x0601
                            1538 -> "GALILEO (F/NAV)"   // 0x0602
                            else -> "UNKNOWN TYPE ($typeId)"
                        }
                        stats[sysName] = stats.getOrDefault(sysName, 0) + 1
                    }
                }
            }
        }
        return stats
    }
}