package com.comicviewer.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val PURCHASE_TOKEN_KEY = "purchase_token"
private const val ACKNOWLEDGEMENT_DEADLINE_KEY = "acknowledgement_deadline"
private const val ACKNOWLEDGEMENT_WORK_PREFIX = "pro_purchase_acknowledgement_"
private const val ACKNOWLEDGEMENT_WINDOW_HOURS = 70L
private const val FOREGROUND_ACKNOWLEDGEMENT_GRACE_SECONDS = 10L

internal enum class ProAcknowledgeAction {
    Success,
    Retry,
    Failure,
}

internal fun proAcknowledgeActionFor(responseCode: Int): ProAcknowledgeAction = when {
    responseCode == BillingClient.BillingResponseCode.OK -> ProAcknowledgeAction.Success
    isRecoverableBillingResponseCode(responseCode) -> ProAcknowledgeAction.Retry
    else -> ProAcknowledgeAction.Failure
}

internal fun proAcknowledgementDeadlineMillis(
    detectedAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    windowMs: Long = TimeUnit.HOURS.toMillis(ACKNOWLEDGEMENT_WINDOW_HOURS),
): Long {
    require(windowMs > 0L)
    val baseTimeMs = detectedAtMs.takeIf { it > 0L } ?: nowMs
    return if (baseTimeMs > Long.MAX_VALUE - windowMs) Long.MAX_VALUE else baseTimeMs + windowMs
}

internal fun enqueueProPurchaseAcknowledgement(
    context: Context,
    purchaseToken: String,
) {
    if (purchaseToken.isBlank()) return
    val deadlineMs = proAcknowledgementDeadlineMillis(detectedAtMs = System.currentTimeMillis())
    val request = OneTimeWorkRequestBuilder<ProPurchaseAcknowledgeWorker>()
        .setInputData(
            workDataOf(
                PURCHASE_TOKEN_KEY to purchaseToken,
                ACKNOWLEDGEMENT_DEADLINE_KEY to deadlineMs,
            ),
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setInitialDelay(FOREGROUND_ACKNOWLEDGEMENT_GRACE_SECONDS, TimeUnit.SECONDS)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS,
        )
        .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        acknowledgementWorkName(purchaseToken),
        ExistingWorkPolicy.KEEP,
        request,
    )
}

internal fun cancelProPurchaseAcknowledgement(context: Context, purchaseToken: String) {
    if (purchaseToken.isBlank()) return
    WorkManager.getInstance(context.applicationContext)
        .cancelUniqueWork(acknowledgementWorkName(purchaseToken))
}

private fun acknowledgementWorkName(purchaseToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(purchaseToken.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(radix = 16).padStart(2, '0')
        }
    return ACKNOWLEDGEMENT_WORK_PREFIX + digest
}

class ProPurchaseAcknowledgeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val purchaseToken = inputData.getString(PURCHASE_TOKEN_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val deadlineMs = inputData.getLong(ACKNOWLEDGEMENT_DEADLINE_KEY, 0L)
        if (deadlineMs <= 0L || System.currentTimeMillis() >= deadlineMs) {
            return Result.failure()
        }

        val billingClient = BillingClient.newBuilder(applicationContext)
            .setListener { _, _ -> }
            .enableAutoServiceReconnection()
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .build()

        return try {
            val setupResult = billingClient.connect()
            when (proAcknowledgeActionFor(setupResult.responseCode)) {
                ProAcknowledgeAction.Success -> Unit
                ProAcknowledgeAction.Retry -> return Result.retry()
                ProAcknowledgeAction.Failure -> return Result.failure()
            }

            val acknowledgementResult = billingClient.acknowledge(purchaseToken)
            when (proAcknowledgeActionFor(acknowledgementResult.responseCode)) {
                ProAcknowledgeAction.Success -> Result.success()
                ProAcknowledgeAction.Retry -> Result.retry()
                ProAcknowledgeAction.Failure -> Result.failure()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        } finally {
            billingClient.endConnection()
        }
    }
}

private suspend fun BillingClient.connect(): BillingResult =
    suspendCancellableCoroutine { continuation ->
        startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(billingResult))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.success(
                                BillingResult.newBuilder()
                                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                    .build(),
                            ),
                        )
                    }
                }
            },
        )
    }

private suspend fun BillingClient.acknowledge(purchaseToken: String): BillingResult =
    suspendCancellableCoroutine { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        acknowledgePurchase(params) { billingResult ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(billingResult))
            }
        }
    }
