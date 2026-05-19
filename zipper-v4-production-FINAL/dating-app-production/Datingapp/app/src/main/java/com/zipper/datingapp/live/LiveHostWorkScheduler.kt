package com.zipper.datingapp.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules background verification that a solo host session was torn down after app kill. */
object LiveHostWorkScheduler {
    private const val PERIODIC_UNIQUE = "zipper_live_host_teardown_periodic"
    private const val ONCE_UNIQUE = "zipper_live_host_teardown_once"

    fun schedule(context: Context) {
        val appCtx = context.applicationContext
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val periodic =
            PeriodicWorkRequestBuilder<LiveHostTeardownWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            PERIODIC_UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        val once =
            OneTimeWorkRequestBuilder<LiveHostTeardownWorker>()
                .setConstraints(constraints)
                .setInitialDelay(90, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            ONCE_UNIQUE,
            ExistingWorkPolicy.REPLACE,
            once,
        )
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork(PERIODIC_UNIQUE)
        wm.cancelUniqueWork(ONCE_UNIQUE)
    }
}
