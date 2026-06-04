import java.time.LocalDateTime
import kotlin.math.round

object GnssMath {
    const val SPEED_OF_LIGHT = 299792458.0 // [m/s]
    const val GPS_WEEKSECS = 604800 // Number of seconds in a week
    const val DAYSEC = 86400 // Number of seconds in a day
    const val NS_TO_S = 1.0e-9
    const val BDST_TO_GPST = 14.0 // Leap seconds difference between Beidou and GPS
    const val GLOT_TO_UTC = 10800.0 // Time difference between GLONASS and UTC in seconds (3h)
    const val CURRENT_GPS_LEAP_SECOND = 18.0

    // GPS epoch zero: January 6, 1980
    val GPSTIME_EPOCH: LocalDateTime = LocalDateTime.of(1980, 1, 6, 0, 0, 0, 0)

    /**
     * Correction for the "Week Crossover",
     * needed if the signal has been traveling so long that it exceeds the GPS week limit.
     */
    fun checkWeekCrossover(tRxSeconds: Double, tTxSeconds: Double): Double {
        var tau = tRxSeconds - tTxSeconds
        if (tau > GPS_WEEKSECS / 2.0) {
            val delSec = round(tau / GPS_WEEKSECS) * GPS_WEEKSECS
            val rhoSec = tau - delSec
            tau = if (rhoSec > 10.0) 0.0 else rhoSec
        }
        return tau
    }

    /**
     * GLONASS time convertion (Time of Day) to GPS time (Time of Week)
     */
    fun glotToGpst(gpstCurrentEpoch: LocalDateTime, todSeconds: Double): Double {
        val todSec = todSeconds.toLong()

        // Approximate GLONASS time (Moscow Time without leap seconds)
        val gloEpoch = gpstCurrentEpoch
            .plusHours(3)
            .minusSeconds(CURRENT_GPS_LEAP_SECOND.toLong())

        // Day of the week (1-7), adjusted to seconds
        val dayOfWeekSec = gloEpoch.dayOfWeek.value * DAYSEC

        return dayOfWeekSec + todSeconds - GLOT_TO_UTC + CURRENT_GPS_LEAP_SECOND
    }
}