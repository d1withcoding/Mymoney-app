package com.pennywiseai.tracker.data.statement

/** Identifies legacy imported statement rows when filtering a shared card. */
internal fun isStatementSender(sender: String): Boolean =
    sender == "STATEMENT" || sender.endsWith(" PDF")
