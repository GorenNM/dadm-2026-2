package co.edu.unal.aichat.data

object ChatConfig {

    /**
     * Modelo Gemini "Flash" estable (GA) más nuevo que la documentación de Firebase AI Logic
     * (https://firebase.google.com/docs/ai-logic/models) lista para el Gemini Developer API, consultada
     * el 2026-11-25. Para cambiar de modelo basta con editar esta constante.
     */
    const val MODEL_NAME = "gemini-3.8-flash"

    const val SYSTEM_INSTRUCTION =
        "You are a friendly assistant inside a mobile app. Reply in the same language the user writes in. " +
            "Be concise: short paragraphs, and simple bullet points when they help."

    /** Mensajes que se mandan al modelo y que sobreviven a la muerte del proceso. */
    const val MAX_HISTORY_MESSAGES = 40

    /** Tope del texto guardado en el SavedStateHandle (el Bundle del sistema es de ~1 MB para toda la app). */
    const val MAX_SAVED_CHARS = 60_000
}
