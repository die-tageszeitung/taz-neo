package de.taz.app.android

import java.util.regex.Pattern

const val TAZ_AUTH_HEADER = "X-tazAppAuthKey"
const val GRAPHQL_ENDPOINT = "https://dl.taz.de/appGraphQl"

const val DEBUG_VERSION_DOWNLOAD_ENDPOINT = "https://dl.taz.de/down/taz.neo.debug.apk"

// UI
const val DEFAULT_MOMENT_RATIO = "W,1.5:1"
const val DEFAULT_NAV_DRAWER_FILE_NAME = "navButton.taz.normal.png"
const val SUBSCRIPTION_EMAIL_ADDRESS = "app@taz.de"
const val WEBVIEW_DRAG_SENSITIVITY_FACTOR = 2
const val WEBVIEW_HTML_FILE = "htmlFile"
const val WEBVIEW_HTML_FILE_TERMS = "welcomeTerms.html"
const val WEBVIEW_HTML_FILE_REVOCATION = "welcomeRevocation.html"
const val WEBVIEW_JQUERY_FILE = "jquery-3.min.js"
const val WEEKEND_TYPEFACE_RESOURCE_FILE_NAME="knile-semibold-webfont.woff"

// Preferences
const val PREFERENCES_DOWNLOADS = "preferences_downloads"
const val PREFERENCES_GENERAL = "preferences_general"
const val PREFERENCES_TAZAPICSS = "preferences_tazapicss"
const val PREFERENCES_LOG_TRACE_LENGTH = 100

// General Preferences
const val DRAWER_SHOW_NUMBER = 1
const val PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER = "DRAWER_SHOWN_NUMBER"

const val RFC5322_PATTERN_STRING =
    """(?:[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"""

const val LOADING_SCREEN_FADE_OUT_TIME = 500L
const val CONNECTION_FAILURE_BACKOFF_TIME_MS = 1000L
const val GRAPHQL_RETRY_LIMIT = 25

// onSaveInstanceState
const val DISPLAYABLE_NAME = "displayableName"
