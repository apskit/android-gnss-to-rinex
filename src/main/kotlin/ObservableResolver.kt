import kotlin.math.round

object ObservableResolver {

    // Return obseervation code for each measurement
    fun getObsCode(measurement: RawMeasurement): String {
        val frequency = measurement.carrierFrequencyHz ?: (154 * 10.23e6)
        val band = getRnxBandFromFreq(frequency)
        val attr = getRnxAttr(band, measurement.constellation, measurement.state)
        return "$band$attr"
    }

    // Obtain the frequency band
    private fun getRnxBandFromFreq(frequency: Double): Int {
        val ifreq = round(frequency / 10.23e6).toInt()
        return when {
            ifreq >= 154 -> 1 // GPS L1, GAL E1, QZSS L1, GLO L1
            ifreq == 115 -> 5 // GPS L5, GAL E5, QZSS L5
            ifreq == 153 -> 2 // BDS B1I
            else -> 1 // Cannot get RINEX frequency band
        }
    }

    // Generate the RINEX attribute from a given band
    private fun getRnxAttr(band: Int, constellation: Constellation, state: Int): Char {
        var attr = 'C'

        // Galileo E1C / E1B
        if (band == 1 && constellation == Constellation.GALILEO) {
            if (!state.hasState(GnssState.GAL_E1C_2ND_CODE_LOCK) && state.hasState(GnssState.GAL_E1B_PAGE_SYNC)) {
                attr = 'B'
            }
        }
        // Galileo E5, QZSS L5, and GPS L5
        if (band == 5) attr = 'Q'

        // Beidou B1I
        if (band == 2 && constellation == Constellation.BEIDOU) attr = 'I'

        return attr
    }
}