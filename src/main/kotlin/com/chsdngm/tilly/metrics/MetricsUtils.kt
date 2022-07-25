package com.chsdngm.tilly.metrics

import com.chsdngm.tilly.config.Metadata.Companion.COMMIT_SHA
import com.chsdngm.tilly.exposed.Vote
import com.google.api.Metric
import com.google.api.MetricDescriptor
import com.google.api.MonitoredResource
import com.google.api.gax.core.CredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.monitoring.v3.MetricServiceClient
import com.google.cloud.monitoring.v3.MetricServiceSettings
import com.google.monitoring.v3.*
import com.google.protobuf.Timestamp
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MetricsUtils(credentialsProvider: CredentialsProvider) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CUSTOM_GOOGLEAPIS = "custom.googleapis.com"

        private const val CHAT_ID_LABEL = "chat_id"
        private const val VOTER_ID_LABEL = "voter_id"
        private const val MEME_ID_LABEL = "meme_id"
        private const val THRESHOLD = 5
    }

    private final var metricServiceClient: MetricServiceClient
    private final val timeSeries = mutableListOf<TimeSeries>()
    private final val resource: MonitoredResource
    private final val projectId: String

    init {
        val metricServiceSettings = MetricServiceSettings.newBuilder()
            .setCredentialsProvider(credentialsProvider)
            .build()

        (credentialsProvider.credentials as ServiceAccountCredentials).let {
            projectId = it.projectId
        }

        resource = MonitoredResource.newBuilder().setType("global").putAllLabels(hashMapOf("project_id" to projectId))
                .build()

        metricServiceClient = MetricServiceClient.create(metricServiceSettings)
    }

    fun measureVoteProcessing(vote: Vote) {
        if (COMMIT_SHA == "local") {
            return
        }

        val endTimeMs = System.currentTimeMillis()

        val interval: TimeInterval = TimeInterval.newBuilder()
            .setEndTime(Timestamp.newBuilder()
                .setSeconds(TimeUnit.MILLISECONDS.toSeconds(endTimeMs))
                .setNanos((TimeUnit.MILLISECONDS.toNanos(endTimeMs) % (1000 * 1000 * 1000)).toInt())
            )
            .build()

        val value = TypedValue.newBuilder().setDoubleValue(endTimeMs.toDouble() - vote.created.toEpochMilli().toDouble()).build()
        val point = Point.newBuilder().setValue(value).setInterval(interval).build()

        val labels = mutableMapOf(
            CHAT_ID_LABEL to "${vote.sourceChatId}",
            VOTER_ID_LABEL to "${vote.voterId}",
            MEME_ID_LABEL to "${vote.memeId}",
        )

        val metric = Metric.newBuilder()
            .setType("$CUSTOM_GOOGLEAPIS/votes/processing_duration")
            .putAllLabels(labels)
            .build()

        timeSeries.add(TimeSeries.newBuilder()
            .setMetric(metric)
            .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
            .setResource(resource)
            .addPoints(point)
            .build())

        checkMetricShipment()
    }

    private fun checkMetricShipment() {
        if (timeSeries.size >= THRESHOLD) {
            runCatching {
                val request = CreateTimeSeriesRequest.newBuilder()
                    .setName(ProjectName.of(projectId).toString())
                    .addAllTimeSeries(timeSeries)
                    .build()

                timeSeries.clear()

                metricServiceClient.createTimeSeries(request)
            }.onFailure {
                log.error("Failed to send metrics", it)
            }
        }
    }
}