package com.chsdngm.tilly.metrics

import com.chsdngm.tilly.config.Metadata.Companion.COMMIT_SHA
import com.chsdngm.tilly.model.CommandUpdate
import com.chsdngm.tilly.model.MemeUpdate
import com.chsdngm.tilly.model.Timestampable
import com.chsdngm.tilly.model.VoteUpdate
import com.chsdngm.tilly.similarity.ImageTextRecognizerGcp
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface MetricsUtils {
    fun measureDuration(update: Timestampable): CompletableFuture<Unit>
}

@Component
@ConditionalOnMissingBean(ImageTextRecognizerGcp::class)
class MetricsUtilsLocal: MetricsUtils {

    override fun measureDuration(update: Timestampable): CompletableFuture<Unit> {
        return CompletableFuture()
    }

}

@Component
@Profile("default")
class MetricsUtilsGcp(credentialsProvider: CredentialsProvider): MetricsUtils {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CUSTOM_GOOGLEAPIS = "custom.googleapis.com"

        private const val PROJECT_ID = "project_id"
        private const val THRESHOLD = 5
    }

    private val durationSeries = hashMapOf<Timestampable, TimeSeries>()
    private val voteMergedSeries = mutableListOf<TimeSeries>()
    private val metricServiceClient: MetricServiceClient
    private val resource: MonitoredResource
    private val projectId: String

    init {
        val metricServiceSettings =
            MetricServiceSettings.newBuilder().setCredentialsProvider(credentialsProvider).build()

        (credentialsProvider.credentials as ServiceAccountCredentials).let {
            projectId = it.projectId
        }

        resource = MonitoredResource.newBuilder()
            .setType("global")
            .putAllLabels(hashMapOf(PROJECT_ID to projectId))
            .build()

        metricServiceClient = MetricServiceClient.create(metricServiceSettings)
    }

    override fun measureDuration(update: Timestampable): CompletableFuture<Unit> = CompletableFuture.supplyAsync {
        //TODO refactor
        if (COMMIT_SHA == "local") {
            return@supplyAsync
        }

        val endTimeMs = System.currentTimeMillis()
        val startTimeMs = update.createdAt

        val durationPoint = buildDurationPoint(startTimeMs, endTimeMs)
        val metric = buildMetric(update)

        durationSeries[update] = TimeSeries.newBuilder()
            .setMetric(metric)
            .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
            .setResource(resource)
            .addPoints(durationPoint)
            .build()

        checkMetricShipment()
    }.exceptionally {
        log.error("Failed to send metrics", it)
    }

    private fun buildMetric(update: Timestampable): Metric? {
        val metric = when (update) {
            is VoteUpdate -> Metric.newBuilder()
                .setType("$CUSTOM_GOOGLEAPIS/votes/processing_duration")
                .putAllLabels(update.toLabels())
            is MemeUpdate -> Metric.newBuilder()
                .setType("$CUSTOM_GOOGLEAPIS/memes/processing_duration")
                .putAllLabels(update.toLabels())
            is CommandUpdate -> Metric.newBuilder()
                .setType("$CUSTOM_GOOGLEAPIS/commands/processing_duration")
                .putAllLabels(update.toLabels())
            else -> null
        }

        return metric?.build()
    }

    private fun buildDurationPoint(startTimeMs: Long, endTimeMs: Long): Point {
        val interval: TimeInterval = TimeInterval.newBuilder()
            .setEndTime(
                Timestamp.newBuilder()
                    .setSeconds(TimeUnit.MILLISECONDS.toSeconds(endTimeMs))
                    .setNanos((TimeUnit.MILLISECONDS.toNanos(endTimeMs) % (1000 * 1000 * 1000)).toInt())
            ).build()

        val value = TypedValue.newBuilder()
            .setDoubleValue(endTimeMs.toDouble() - startTimeMs.toDouble())
            .build()

        return Point.newBuilder().setValue(value).setInterval(interval).build()
    }

    private fun buildDurationPoint(startTimeMs: Long, endTimeMs: Long, votesCount: Int): Point {
        val interval: TimeInterval = TimeInterval.newBuilder()
                .setEndTime(
                        Timestamp.newBuilder()
                                .setSeconds(TimeUnit.MILLISECONDS.toSeconds(endTimeMs))
                                .setNanos((TimeUnit.MILLISECONDS.toNanos(endTimeMs) % (1000 * 1000 * 1000)).toInt())
                ).build()

        val value = TypedValue.newBuilder()
                .setDoubleValue(votesCount.toDouble())
                .build()

        return Point.newBuilder().setValue(value).setInterval(interval).build()
    }

    private fun checkMetricShipment() {
        if (durationSeries.size >= THRESHOLD) {
            runCatching {
                val request = CreateTimeSeriesRequest.newBuilder().setName(ProjectName.of(projectId).toString())
                    .addAllTimeSeries(durationSeries.values)
                    .build()

                durationSeries.clear()

                metricServiceClient.createTimeSeries(request)
            }.onFailure {
                log.error("Failed to send metrics", it)
            }
        }

        if (voteMergedSeries.size >= 1) {
            runCatching {
                val request = CreateTimeSeriesRequest.newBuilder().setName(ProjectName.of(projectId).toString())
                        .addAllTimeSeries(voteMergedSeries)
                        .build()

                voteMergedSeries.clear()

                metricServiceClient.createTimeSeries(request)
            }
        }
    }

    private fun VoteUpdate.toLabels() = hashMapOf(
        "chat_id" to this.sourceChatId,
        "voter_id" to "${this.voterId}",
        "message_id" to "${this.messageId}",
        "value" to "${this.voteValue}"
    )

    private fun MemeUpdate.toLabels() = hashMapOf(
        "user_id" to "${this.user.id}",
        "message_id" to "${this.messageId}",
        "file_id" to this.fileId,
    )

    private fun CommandUpdate.toLabels() = hashMapOf(
        "sender_id" to this.senderId,
        "message_id" to "${this.messageId}",
        "chat_id" to this.chatId,
        "text" to this.text,
    )
}