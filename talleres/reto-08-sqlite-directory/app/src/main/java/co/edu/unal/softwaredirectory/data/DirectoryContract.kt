package co.edu.unal.softwaredirectory.data

/** Nombres de tablas y columnas, en un solo lugar (el "contract" de la documentación de Android). */
object DirectoryContract {

    object Companies {
        const val TABLE = "companies"
        const val ID = "_id"
        const val NAME = "name"
        /** [SearchText.normalize] del nombre: se busca sobre esta columna y es la que no se puede repetir. */
        const val NAME_KEY = "name_key"
        const val WEBSITE = "website"
        const val PHONE = "phone"
        const val EMAIL = "email"
        const val PRODUCTS = "products"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }

    /** Tabla intermedia: una fila por cada clasificación de una empresa. */
    object CompanyClassifications {
        const val TABLE = "company_classifications"
        const val COMPANY_ID = "company_id"
        const val CLASSIFICATION = "classification"
    }
}
