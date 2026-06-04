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
}