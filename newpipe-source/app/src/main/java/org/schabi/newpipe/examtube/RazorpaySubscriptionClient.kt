/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.examtube

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object RazorpaySubscriptionClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun createSubscription(idToken: String, callback: (Result<JSONObject>) -> Unit) {
        post(
            path = "/subscription",
            body = JSONObject().put("idToken", idToken),
            callback = callback
        )
    }

    fun verifySubscription(
        idToken: String,
        paymentId: String,
        subscriptionId: String,
        signature: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        post(
            path = "/subscription/verify",
            body = JSONObject()
                .put("idToken", idToken)
                .put("razorpayPaymentId", paymentId)
                .put("razorpaySubscriptionId", subscriptionId)
                .put("razorpaySignature", signature),
            callback = callback
        )
    }

    fun checkSubscription(
        idToken: String,
        subscriptionId: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        post(
            path = "/subscription/access",
            body = JSONObject()
                .put("idToken", idToken)
                .put("razorpaySubscriptionId", subscriptionId),
            callback = callback
        )
    }

    private fun post(
        path: String,
        body: JSONObject,
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val baseUrl = BuildConfig.EXAMTUBE_BILLING_BASE_URL.trimEnd('/')
                require(baseUrl.isNotBlank()) {
                    "ExamTube billing server is not configured. Set examtubeBillingBaseUrl."
                }

                val connection = (URL("$baseUrl/api/examtube$path").openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                if (connection.responseCode !in 200..299) {
                    error("Billing server returned HTTP ${connection.responseCode}: $response")
                }
                JSONObject(response)
            }
            mainHandler.post { callback(result) }
        }
    }
}