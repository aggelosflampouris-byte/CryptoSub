package com.privatemessenger

/** App-wide constants. All URLs and config values live here — never inline. */
object AppConstants {

    // ─── Attachment Upload ────────────────────────────────────────────────────
    /**
     * Catbox.moe anonymous upload endpoint.
     * Attachments are XMTP-encrypted before upload, so public hosting is safe.
     */
    const val ATTACHMENT_UPLOAD_URL = "https://catbox.moe/user/api.php"

    // ─── GitHub Release Metadata ─────────────────────────────────────────────
    /**
     * Used by [AppUpdater] to check for new releases.
     * Values are also injected via BuildConfig.GITHUB_OWNER / GITHUB_REPO
     * so they can be changed at build time without editing this file.
     */
    const val GITHUB_RELEASES_URL =
        "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
}
