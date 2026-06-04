import java.io.BufferedWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RinexFormatter {

    fun writeHeader(
        writer: BufferedWriter,
        firstEpoch: LocalDateTime,
        lastEpoch: LocalDateTime?,
        obsList: Map<Char, List<String>>,
        gloSlotFreqChns: Map<String, Int>,
        recNumber: String = "UNKN",
        recType: String = "UNKN",
        recVersion: String = "UNKN",
        antennaNumber: String = "UNKN",
        antennaType: String = "UNKN"
    ) {
        // RINEX VERSION / TYPE
        writer.write(String.format(Locale.US, "%9.2f           %-20s%-20s%s\n", 3.03, "O", "M", "RINEX VERSION / TYPE"))

        // PGM / RUN BY / DATE
        val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        writer.write(String.format(Locale.US, "%-20s%-20s%-20s%s\n", "android-rinex", "not-available", dateStr, "PGM / RUN BY / DATE"))

        // MARKER NAME / TYPE / OBSERVER
        writer.write(String.format(Locale.US, "%-60s%s\n", "UNKN", "MARKER NAME"))
        writer.write(String.format(Locale.US, "%-60s%s\n", "SMARTPHONE", "MARKER TYPE"))
        writer.write(String.format(Locale.US, "%-20s%-40s%s\n", "UNKN", "UNKN", "OBSERVER / AGENCY"))
        writer.write(String.format(Locale.US, "%-20s%-20s%-20s%s\n", recNumber, recType, recVersion, "REC # / TYPE / VERS"))
        writer.write(String.format(Locale.US, "%-20s%-40s%s\n", antennaNumber, antennaType, "ANT # / TYPE"))
        writer.write(String.format(Locale.US, "%14.4f%14.4f%14.4f                  %s\n", 0.0, 0.0, 0.0, "APPROX POSITION XYZ"))
        writer.write(String.format(Locale.US, "%14.4f%14.4f%14.4f                  %s\n", 0.0, 0.0, 0.0, "ANTENNA: DELTA H/E/N"))

        // SYS / # / OBS TYPES
        for ((sys, obsTypes) in obsList) {
            val lines = obsTypes.chunked(13)
            for ((index, chunk) in lines.withIndex()) {
                val prefix = if (index == 0) "$sys  ${String.format(Locale.US, "%3d", obsTypes.size)}" else "      "
                val obsString = chunk.joinToString("") { String.format(Locale.US, " %3s", it) }
                writer.write(String.format(Locale.US, "%-60s%s\n", prefix + obsString, "SYS / # / OBS TYPES"))
            }
        }

        // TIME OF FIRST OBS
        val firstTimeStr = formatRinexEpoch(firstEpoch)
        writer.write(String.format(Locale.US, "%-60s%s\n", firstTimeStr, "TIME OF FIRST OBS"))

        if (lastEpoch != null) {
            val lastTimeStr = formatRinexEpoch(lastEpoch)
            writer.write(String.format(Locale.US, "%-60s%s\n", lastTimeStr, "TIME OF LAST OBS"))
        }

        // GLONASS SLOT / FRQ
        if (gloSlotFreqChns.isEmpty()) {
            val gloStr = String.format(Locale.US, "%3d ", 0)
            writer.write(String.format(Locale.US, "%-60s%s\n", gloStr, "GLONASS SLOT / FRQ #"))
        } else {
            var res = String.format(Locale.US, "%3d ", gloSlotFreqChns.size)
            for ((sat, chn) in gloSlotFreqChns) {
                res += String.format(Locale.US, "%3s %2d ", sat, chn)
                // RINEX wrapping after 60 characters
                if (res.length >= 60) {
                    writer.write(String.format(Locale.US, "%-60s%s\n", res, "GLONASS SLOT / FRQ #"))
                    res = "    " // New line cut
                }
            }
            if (res.trim().isNotEmpty()) {
                writer.write(String.format(Locale.US, "%-60s%s\n", res, "GLONASS SLOT / FRQ #"))
            }
        }
        // GLONASS COD/PHS/BIS#
        writer.write(String.format(Locale.US, "%-60s%s\n", "", "GLONASS COD/PHS/BIS#"))

        // END OF HEADER
        writer.write(String.format(Locale.US, "%-60s%s\n", "", "END OF HEADER"))
    }

    private fun formatRinexEpoch(epoch: LocalDateTime): String {
        return String.format(Locale.US, "  %04d    %02d    %02d    %02d    %02d    %02d.%07d",
            epoch.year, epoch.monthValue, epoch.dayOfMonth,
            epoch.hour, epoch.minute, epoch.second, epoch.nano / 100)
    }

    fun writeEpoch(writer: BufferedWriter, batch: EpochBatch, obsList: Map<Char, List<String>>) {
        val e = batch.epochTime
        val epochHeader = String.format(Locale.US, "> %04d %02d %02d %02d %02d %02d.%07d  0 %2d\n",
            e.year, e.monthValue, e.dayOfMonth, e.hour, e.minute, e.second, e.nano / 100, batch.measurements.size)
        writer.write(epochHeader)

        // Satellite measurements in the epoch
        for ((satName, measurements) in batch.measurements) {
            writer.write(satName)
            val sysLetter = satName[0]
            val expectedObs = obsList[sysLetter] ?: emptyList()

            for (obsType in expectedObs) {
                val value = measurements[obsType]

                if (value != null && value <= 40e6) {
                    // BeiDou satellites can have long ranges if GEO satellites are used
                    writer.write(String.format(Locale.US, "%14.3f00", value))
                } else {
                    writer.write("                ") // Empty 16 if the measurement is missing
                }
            }
            writer.write("\n")
        }
    }
}