package ir.metra.app.feature.shooting.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShootingDatabaseTest {
    private lateinit var database: ShootingDatabase
    private lateinit var dao: ShootingDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ShootingDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.shootingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun deletingReportCascadesToItsRoutesAndRangeTotalUpdates() = runBlocking {
        val reportId = dao.insertReport(
            ShootingReportEntity(
                projectId = null,
                projectName = "پروژه تست",
                workDateEpochDay = 20_000L,
                workArea = "محدوده ۱",
                employer = "",
                supervisor = "",
                totalMeters = 460L,
                createdAtEpochMilli = 1L,
                updatedAtEpochMilli = 1L,
            ),
        )
        dao.insertOperations(
            listOf(
                ShootingOperationEntity(
                    reportId = reportId,
                    startPointType = "ODC",
                    startPointCode = "A-1",
                    startMeter = 1_120L,
                    endPointType = "FAT",
                    endPointCode = "B-2",
                    endMeter = 1_580L,
                    cableTypeId = 1L,
                    operationMeters = 460L,
                ),
            ),
        )

        assertThat(dao.getOperations(reportId)).hasSize(1)
        assertThat(dao.totalMeters(20_000L, 20_000L)).isEqualTo(460L)

        dao.deleteReport(reportId)

        assertThat(dao.getOperations(reportId)).isEmpty()
        assertThat(dao.totalMeters(20_000L, 20_000L)).isEqualTo(0L)
    }
}
