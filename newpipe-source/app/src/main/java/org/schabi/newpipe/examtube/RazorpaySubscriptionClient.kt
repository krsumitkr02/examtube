/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.examtube

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig

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

                val connection = (
                    URL("$baseUrl/api/examtube$path").openConnection()
                        as HttpURLConnection
                    ).apply {
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

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (responseCode !in 200..299) {
                    val serverMessage = runCatching {
                        JSONObject(response).optString("error")
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    error(
                        serverMessage
                            ?: "Billing server returned HTTP $responseCode."
                    )
                }
                JSONObject(response)
            }
            mainHandler.post { callback(result) }
        }
    }
}
