package com.pennywiseai.tracker.core


/**
 * Application-wide constants to avoid hardcoded values
 */
object Constants {
    
    /**
     * SMS Processing Configuration
     */
    object SmsProcessing {
        const val DEFAULT_BATCH_SIZE = 100
        const val SMS_PREVIEW_LENGTH = 200
        const val QUERY_LIMIT = 100
        const val INITIAL_SCAN_MONTHS = 3
        const val SCANNING_DELAY_MS = 3000L
        /** Stored in [last_scan_period] when SMS scan period is set to all time. */
        const val SCAN_PERIOD_ALL_TIME = -1
        /** Stored in [last_scan_period] when SMS scan period is a custom start date. */
        const val SCAN_PERIOD_CUSTOM_DATE = -2
    }
    
    /**
     * UI Configuration - Moved to ui/theme/Dimensions.kt for better organization
     * Keeping only non-dimension constants here
     */
    object UI {
        const val BUTTON_WIDTH_RATIO = 0.8f
        const val PROGRESS_STROKE_WIDTH = 2f
    }
    
    /**
     * Database Configuration
     */
    object Database {
        const val DATABASE_NAME = "pennywise_database"
        const val CURRENT_VERSION = 2
        const val TRANSACTION_HASH_DEFAULT = ""
    }
    
    /**
     * WorkManager Configuration
     */
    object WorkManager {
        const val SMS_READER_WORK_NAME = "sms_reader_work"
        const val PERIODIC_SCAN_INTERVAL_HOURS = 24L
        const val INITIAL_DELAY_MINUTES = 15L
    }
    
    /**
     * Parsing Configuration
     */
    object Parsing {
        const val MIN_MERCHANT_NAME_LENGTH = 2
        const val MD5_ALGORITHM = "MD5"
        const val AMOUNT_SCALE = 2
        const val CONFIDENCE_PATTERN_BASED = 0.7f
        const val CONFIDENCE_AI_BASED = 0.9f
    }
    
    /**
     * Navigation Routes
     */
    object Routes {
        const val HOME = "home"
        const val TRANSACTIONS = "transactions"
        const val ANALYTICS = "analytics"
        const val CHAT = "chat"
        const val SETTINGS = "settings"
    }
    
    /**
     * LLM Model Configuration
     *
     * A local chat model is verified against a pinned SHA-256 before it is ever
     * loaded. A replacement model must use a fresh version; bump [MODEL_VERSION]
     * and [MODEL_SHA256] together.
     */
    object ModelDownload {
        const val MODEL_FILE_NAME = "Qwen2.5-1.5B-Instruct-q8-ekv4096.litertlm"

        /** Bump when a new model build is published under a new versioned key. */
        const val MODEL_VERSION = "v1"

        /**
         * Lowercase-hex SHA-256 of the model bytes. A local file is hashed and compared
         * against this before it is loaded;
         * a mismatch is rejected and the file deleted. Recompute and bump on every
         * model change. Leave blank ONLY to intentionally disable verification (a
         * warning is logged and any file is accepted).
         */
        const val MODEL_SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"

        const val MODEL_SIZE_MB = 1524L
        const val MODEL_SIZE_BYTES = 1_597_931_520L
        const val REQUIRED_SPACE_BYTES = 3_195_863_040L // ~3.0GB (2x model size for safety)
    }

}
