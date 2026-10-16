package com.resume.analytics.domain

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Degrade ladder state (07-scale-out.md). First shed candidate for
 * analytics-service: realtime aggregation. Shedding stops counter updates but
 * intake stays uniform 202 and acknowledged (income path protected); reads
 * degrade to honest zeros via the existing partial labels.
 */
@Component
class DegradeState {
    val shedRealtime = AtomicBoolean(false)
}
