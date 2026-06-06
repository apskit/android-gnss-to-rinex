import java.io.BufferedWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RinexNavFormatter {

    fun writeNavHeader(writer: BufferedWriter, navStats: Map<String, Int>) {
        // RINEX VERSION / TYPE
        writer.write(String.format(Locale.US, "%9.2f           %-20s%-20s%s\n", 3.03, "N", "M", "RINEX VERSION / TYPE"))

        // PGM / RUN BY / DATE
        val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        writer.write(String.format(Locale.US, "%-20s%-20s%-20s%s\n", "android-rinex", "not-available", dateStr, "PGM / RUN BY / DATE"))

        // COMMENT (TODO)
        writer.write(String.format(Locale.US, "%-60s%s\n", "Generated NAV file without decoded ephemeris.", "COMMENT"))

        if (navStats.isEmpty()) {
            writer.write(String.format(Locale.US, "%-60s%s\n", "No NAV messages found in the input log.", "COMMENT"))
        } else {
            for ((sysName, count) in navStats) {
                val msg = "Found $count raw NAV messages for $sysName"
                writer.write(String.format(Locale.US, "%-60s%s\n", msg, "COMMENT"))
            }
        }

        // LEAP SECONDS
        val leapSeconds = GnssMath.CURRENT_GPS_LEAP_SECOND.toInt()
        writer.write(String.format(Locale.US, "%6d                                                      %s\n", leapSeconds, "LEAP SECONDS"))

        // 4. END OF HEADER
        writer.write(String.format(Locale.US, "%-60s%s\n", "", "END OF HEADER"))
    }
}