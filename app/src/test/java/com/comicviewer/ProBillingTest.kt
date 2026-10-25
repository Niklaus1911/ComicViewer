package com.comicviewer

import com.android.billingclient.api.BillingClient
import com.comicviewer.billing.ProAcknowledgeAction
import com.comicviewer.billing.ProBillingMessage
import com.comicviewer.billing.billingRetryDelayMillis
import com.comicviewer.billing.isRecoverableBillingResponseCode
import com.comicviewer.billing.proAcknowledgeActionFor
import com.comicviewer.billing.proAcknowledgementDeadlineMillis
import com.comicviewer.billing.userMessageForBillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProBillingTest {
    @Test
    fun isRecoverableBillingResponseCode_marksTransientPurchaseFailuresRecoverable() {
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.NETWORK_ERROR))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ERROR))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED))
        assertTrue(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_NOT_OWNED))
    }

    @Test
    fun isRecoverableBillingResponseCode_doesNotRetryUserCancelOrFatalSetupFailures() {
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.USER_CANCELED))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR))
        assertFalse(isRecoverableBillingResponseCode(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE))
    }

    @Test
    fun userMessageForBillingResponseCode_hidesTechnicalMessagesForRecoverableFailures() {
        assertEquals(
            ProBillingMessage.CheckingPurchase,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE),
        )
        assertEquals(
            ProBillingMessage.CheckingPurchase,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED),
        )
        assertEquals(
            ProBillingMessage.PurchaseFailed,
            userMessageForBillingResponseCode(BillingClient.BillingResponseCode.DEVELOPER_ERROR),
        )
    }

    @Test
    fun billingRetryDelayMillis_usesCappedExponentialBackoff() {
        assertEquals(1_500L, billingRetryDelayMillis(attempt = 1))
        assertEquals(3_000L, billingRetryDelayMillis(attempt = 2))
        assertEquals(6_000L, billingRetryDelayMillis(attempt = 3))
        assertEquals(60_000L, billingRetryDelayMillis(attempt = 20))
    }

    @Test
    fun proAcknowledgeActionFor_retriesOnlyRecoverableFailures() {
        assertEquals(
            ProAcknowledgeAction.Success,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.OK),
        )
        assertEquals(
            ProAcknowledgeAction.Retry,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.NETWORK_ERROR),
        )
        assertEquals(
            ProAcknowledgeAction.Retry,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.ITEM_NOT_OWNED),
        )
        assertEquals(
            ProAcknowledgeAction.Failure,
            proAcknowledgeActionFor(BillingClient.BillingResponseCode.DEVELOPER_ERROR),
        )
    }

    @Test
    fun proAcknowledgementDeadlineMillis_usesDetectionTimeAndHandlesBadValues() {
        assertEquals(
            1_500L,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = 1_000L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
        assertEquals(
            2_500L,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = 0L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            proAcknowledgementDeadlineMillis(
                detectedAtMs = Long.MAX_VALUE - 100L,
                nowMs = 2_000L,
                windowMs = 500L,
            ),
        )
    }
}
