package ir.metra.app.feature.shooting.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShootingOperationDraftTest {
    @Test fun decreasingCableCounterUsesAbsoluteDistance() {
        val operation = ShootingOperationDraft("ODC", "1", 1580, "FAT", "2", 1120, 1)
        assertThat(operation.meters).isEqualTo(460L)
    }
    @Test fun increasingCableCounterUsesDifference() {
        val operation = ShootingOperationDraft("ODC", "1", 1120, "FAT", "2", 1580, 1)
        assertThat(operation.meters).isEqualTo(460L)
    }
    @Test fun equalCableCountersProduceZeroMeters() {
        val operation = ShootingOperationDraft("ODC", "1", 1200, "FAT", "2", 1200, 1)
        assertThat(operation.meters).isEqualTo(0L)
    }
}
