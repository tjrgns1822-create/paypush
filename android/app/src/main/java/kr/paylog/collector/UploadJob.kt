package kr.paylog.collector

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * 인터넷이 연결되면 대기열을 보내는 작업. 실패하면 30초부터 간격을 늘려 가며 다시 시도한다.
 * 6시간마다 한 번 도는 작업도 따로 둬서, 혹시 남은 행이 있으면 보낸다.
 */
class UploadJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val ok = Uploader.flush(applicationContext)
            jobFinished(params, !ok)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val NOW_ID = 1001
        private const val PERIODIC_ID = 1002

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(NOW_ID) != null) return
            js.schedule(
                JobInfo.Builder(NOW_ID, ComponentName(context, UploadJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build()
            )
        }

        fun schedulePeriodic(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(PERIODIC_ID) != null) return
            js.schedule(
                JobInfo.Builder(PERIODIC_ID, ComponentName(context, UploadJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(6 * 60 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
            )
        }
    }
}
