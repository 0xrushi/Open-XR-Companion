package com.inmoair.xrcompanion.client.data

import kotlinx.serialization.Serializable

/**
 * A user-defined remote button.
 *
 * [name]  — label shown on the button
 * [macro] — what to send when tapped:
 *           type = "text"  → typed verbatim on the glasses
 *           type = "enter" → sends Enter key
 *           type = "tab"   → sends Tab key
 *           type = "esc"   → sends Escape key
 */
@Serializable
data class CustomButton(
    val id: String,
    val name: String,
    val macro: String,
    val type: String = "text",   // "text" | "enter" | "tab" | "esc"
)
