package co.edu.unal.softwaredirectory.ui

import androidx.annotation.StringRes
import co.edu.unal.softwaredirectory.R
import co.edu.unal.softwaredirectory.data.Classification

/** El texto de cada clasificación (en la base se guarda el código, no el texto traducido). */
@get:StringRes
val Classification.labelRes: Int
    get() = when (this) {
        Classification.CONSULTING -> R.string.classification_consulting
        Classification.CUSTOM_DEVELOPMENT -> R.string.classification_custom_development
        Classification.SOFTWARE_FACTORY -> R.string.classification_software_factory
    }
