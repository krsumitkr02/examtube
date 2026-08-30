/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const val NEWPIPE_VERSION_SDK_COMPILE_MAJOR = 37
const val NEWPIPE_VERSION_SDK_COMPILE_MINOR = 0
const val NEWPIPE_VERSION_SDK_MIN = 23
const val NEWPIPE_VERSION_SDK_TARGET = 35

const val NEWPIPE_VERSION_CODE = 1015
const val NEWPIPE_VERSION_NAME = "0.29.1"

const val NEWPIPE_APPLICATION_ID_OLD = "org.schabi.newpipe"
const val NEWPIPE_APPLICATION_ID_NEW = "net.newpipe.app"

// ExamTube branding is intentionally kept in the build configuration so the
// upstream source packages can remain intact while the published app identity
// is changed safely.
const val EXAMTUBE_APPLICATION_ID = "app.examtube.com"
const val EXAMTUBE_APP_NAME = "ExamTube"
const val EXAMTUBE_SOURCE_REPOSITORY = "https://github.com/TeamNewPipe/NewPipe.git"
