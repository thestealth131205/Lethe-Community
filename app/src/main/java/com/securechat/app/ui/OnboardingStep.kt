package com.securechat.app.ui

/**
 * Definiert alle Schritt-für-Schritt-Onboarding-Tipps der App in der richtigen Reihenfolge.
 * Jeder Schritt hat eine stabile ID, die in DataStore gespeichert wird.
 */
enum class OnboardingStep(val id: String) {
    /** Pfeil auf das + FAB in der Kontaktliste */
    ADD_CONTACT_FAB("add_contact_fab"),

    /** Pfeil auf "Kontakt hinzufügen" (Mini-FAB) nach dem Aufklappen */
    ADD_CONTACT_BUTTON("add_contact_button"),

    /** Hervorhebung des Telefonnummer-Feldes im AddContactDialog */
    ADD_CONTACT_PHONE("add_contact_phone"),

    /** Info + Pfeil auf ⋮-Menü (nach gesendeter Kontaktanfrage) */
    PRIVACY_THREE_DOTS("privacy_three_dots"),

    /** Pfeil auf "Account" im aufgeklappten Dropdown-Menü */
    PRIVACY_ACCOUNT_ITEM("privacy_account_item"),

    /** Pfeil auf "Datenschutz & Privatsphäre" im Account-Screen */
    PRIVACY_ENTRY("privacy_entry"),

    /** Sprechblasen-Overlay auf dem Datenschutz-Screen */
    PRIVACY_SPEECH_BUBBLE("privacy_speech_bubble"),

    /** Feier-Overlay nach der ersten verschickten Nachricht */
    FIRST_MESSAGE("first_message");

    companion object {
        /** Gibt den nächsten noch nicht abgeschlossenen Schritt zurück (null = alles erledigt). */
        fun nextStep(completedIds: Set<String>): OnboardingStep? =
            entries.firstOrNull { it.id !in completedIds }

        fun fromId(id: String): OnboardingStep? = entries.firstOrNull { it.id == id }
    }
}
