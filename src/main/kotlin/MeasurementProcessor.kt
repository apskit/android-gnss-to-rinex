import java.time.LocalDateTime

/** Output structure of a single RINEX epoch */
data class EpochBatch(
    val epochTime: LocalDateTime,
    val measurements: Map<String, Map<String, Double>>
)

class MeasurementProcessor(
    private val integerize: Boolean = false,
    private val useFixedFullBias: Boolean = false
) {
    // Save first FullBiasNanos
    private var fixedFullBiasNanos: Double? = null

    /**
     * Convert raw stream from parser to grouped RINEX epochs.
     */
    fun process(rawSequence: Sequence<RawMeasurement>): Sequence<EpochBatch> = sequence {
        var currentBatchTime = -1.0
        val currentBatch = mutableListOf<RawMeasurement>()

        for (measurement in rawSequence) {
            // Group measurements based on the same measurement moment (TimeNanos)
            if (currentBatchTime == -1.0 || measurement.timeNanos == currentBatchTime) {
                currentBatch.add(measurement)
                currentBatchTime = measurement.timeNanos
            } else {
                yield(computeEpoch(currentBatch)) // Return batch to RINEX
                currentBatch.clear()
                currentBatch.add(measurement)
                currentBatchTime = measurement.timeNanos
            }
        }
        if (currentBatch.isNotEmpty()) {
            yield(computeEpoch(currentBatch))
        }
    }

    private fun computeEpoch(batch: List<RawMeasurement>): EpochBatch {
        val processedData = mutableMapOf<String, MutableMap<String, Double>>()
        var epochTime = GnssMath.GPSTIME_EPOCH

        for (meas in batch) {
            //if (!isValid(meas)) continue

            // TODO SYS fix
            // if (meas.constellation == Constellation.GLONASS && meas.svid > 50) continue

            // Constant clock support (-b)
            if (useFixedFullBias && fixedFullBiasNanos == null) {
                fixedFullBiasNanos = meas.fullBiasNanos
            }
            val activeFullBias = if (useFixedFullBias) fixedFullBiasNanos!! else meas.fullBiasNanos

            // 1. TIME CALCULATIONS
            val gpsWeek = kotlin.math.floor(-activeFullBias * GnssMath.NS_TO_S / GnssMath.GPS_WEEKSECS)
            val localEstGpsTime = meas.timeNanos - (activeFullBias + meas.biasNanos)
            val gpsSow = localEstGpsTime * GnssMath.NS_TO_S - gpsWeek * GnssMath.GPS_WEEKSECS

            val frac = if (integerize) gpsSow - kotlin.math.floor(gpsSow + 0.5) else 0.0

            // Build an epoch date
            val secondsToAdd = (gpsSow - frac).toLong()
            val nanosToAdd = ((gpsSow - frac - secondsToAdd) * 1e9).toLong()
            val currentEpoch = GnssMath.GPSTIME_EPOCH
                .plusWeeks(gpsWeek.toLong())
                .plusSeconds(secondsToAdd)
                .plusNanos(nanosToAdd)

            epochTime = currentEpoch

            val tRxSeconds = gpsSow - meas.timeOffsetNanos * GnssMath.NS_TO_S
            val wavelength = GnssMath.SPEED_OF_LIGHT / (meas.carrierFrequencyHz ?: (154 * 10.23e6))

            // 2. PSEUDORANGE CALCULATIONS (C)
            var range = 0.0
            if (isSyncValid(meas)) {
                val tTxSeconds = when (meas.constellation) {
                    Constellation.GLONASS -> GnssMath.glotToGpst(
                        currentEpoch,
                        meas.receivedSvTimeNanos * GnssMath.NS_TO_S
                    )

                    Constellation.BEIDOU -> meas.receivedSvTimeNanos * GnssMath.NS_TO_S + GnssMath.BDST_TO_GPST
                    else -> meas.receivedSvTimeNanos * GnssMath.NS_TO_S
                }

                val tau = GnssMath.checkWeekCrossover(tRxSeconds, tTxSeconds)
                range = tau * GnssMath.SPEED_OF_LIGHT
                if (integerize) {
                    range -= frac * meas.pseudorangeRateMetersPerSecond
                }
            }
            // 3. CARRIER PHASE (L), DOPPLER (D) and SNR (S) CALCULATIONS
            var cphase = 0.0
            if (meas.accumulatedDeltaRangeState.hasState(AdrState.VALID)) {
                cphase = meas.accumulatedDeltaRangeMeters / wavelength
            }
            val doppler = -meas.pseudorangeRateMetersPerSecond / wavelength
            val cn0 = meas.cn0DbHz

            // Save to dictionary
            val obsCode = ObservableResolver.getObsCode(meas)
            val satMap = processedData.getOrPut(meas.satName) { mutableMapOf() }

            // C, L, D, S RINEX codes (Code, Phase, Doppler, Signal)
            satMap["C$obsCode"] = range
            satMap["L$obsCode"] = cphase
            satMap["D$obsCode"] = doppler
            satMap["S$obsCode"] = cn0
        }

        return EpochBatch(epochTime, processedData)
    }

    /** Validate sync state */
    private fun isSyncValid(meas: RawMeasurement): Boolean {
        val state = meas.state

        when (meas.constellation) {
            Constellation.GPS, Constellation.QZSS, Constellation.BEIDOU -> {
                if (!state.hasState(GnssState.CODE_LOCK)) return false
                if (!state.hasState(GnssState.TOW_DECODED)) return false
                if (!state.hasState(GnssState.BIT_SYNC)) return false
                if (!state.hasState(GnssState.SUBFRAME_SYNC)) return false
                if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
            }
            Constellation.SBAS -> {
                if (!state.hasState(GnssState.CODE_LOCK)) return false
                if (!state.hasState(GnssState.TOW_DECODED)) return false
                if (!state.hasState(GnssState.BIT_SYNC)) return false
                if (!state.hasState(GnssState.SYMBOL_SYNC)) return false
                if (!state.hasState(GnssState.SBAS_SYNC)) return false
                if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
            }
            Constellation.GLONASS -> {
                if (!state.hasState(GnssState.CODE_LOCK)) return false
                if (!state.hasState(GnssState.SYMBOL_SYNC)) return false
                if (!state.hasState(GnssState.BIT_SYNC)) return false
                if (!state.hasState(GnssState.GLO_TOD_DECODED)) return false
                if (!state.hasState(GnssState.GLO_STRING_SYNC)) return false
                if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
            }
            Constellation.GALILEO -> {
                // Calculate the band (1 or 5) (TODO: Pth get_rnx_band_from_freq)
                val frequency = meas.carrierFrequencyHz ?: (154 * 10.23e6)
                val ifreq = kotlin.math.round(frequency / 10.23e6).toInt()
                val frequencyBand = if (ifreq == 115) 5 else 1

                if (frequencyBand == 1) {
                    if (!state.hasState(GnssState.GAL_E1BC_CODE_LOCK)) return false

                    // No E1C flag indicates presence of E1B code
                    if (!state.hasState(GnssState.GAL_E1C_2ND_CODE_LOCK)) {
                        if (!state.hasState(GnssState.TOW_DECODED)) return false
                        if (!state.hasState(GnssState.BIT_SYNC)) return false
                        if (!state.hasState(GnssState.GAL_E1B_PAGE_SYNC)) return false
                        if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
                    } else {
                        // State value indicates presence of E1C code
                        if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
                    }
                } else { // Measurement is E5a (TODO: ..)
                    if (!state.hasState(GnssState.CODE_LOCK)) return false
                    if (!state.hasState(GnssState.TOW_DECODED)) return false
                    if (!state.hasState(GnssState.BIT_SYNC)) return false
                    if (!state.hasState(GnssState.SUBFRAME_SYNC)) return false
                    if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
                }
            }
            Constellation.UNKNOWN -> {
                if (!state.hasState(GnssState.CODE_LOCK)) return false
                if (!state.hasState(GnssState.TOW_DECODED)) return false
                if (state.hasState(GnssState.MSEC_AMBIGUOUS)) return false
            }
        }
        return true
    }
}