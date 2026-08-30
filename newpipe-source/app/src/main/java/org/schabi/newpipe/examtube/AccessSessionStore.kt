/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.examtube

import android.content.Context

/**
 * Stores only the result of a server-verified entitlement.
 *
 * The source of truth is the ExamTube billing server. This cache is used to
 * keep the app usable between launches after the server has verified access.
 */
class AccessSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun hasVerifiedAccess(): Boolean =
        preferences.getBoolean(KEY_VERIFIED_ACCESS, false) &&
            !preferences.getString(KEY_GOOGLE_USER_ID, null).isNullOrBlank() &&
            !preferences.getString(KEY_SUBSCRIPTION_ID, null).isNullOrBlank()

    fun saveVerifiedAccess(googleUserId: String, subscriptionId: String) {
        preferences.edit()
            .putBoolean(KEY_VERIFIED_ACCESS, true)
            .putString(KEY_GOOGLE_USER_ID, googleUserId)
            .putString(KEY_SUBSCRIPTION_ID, subscriptionId)
            .apply()
    }

    fun googleUserId(): String? = preferences.getString(KEY_GOOGLE_USER_ID, null)

    fun subscriptionId(): String? = preferences.getString(KEY_SUBSCRIPTION_ID, null)

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "examtube_access"
        private const val KEY_VERIFIED_ACCESS = "verified_access"
        private const val KEY_GOOGLE_USER_ID = "google_user_id"
        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
    }
}