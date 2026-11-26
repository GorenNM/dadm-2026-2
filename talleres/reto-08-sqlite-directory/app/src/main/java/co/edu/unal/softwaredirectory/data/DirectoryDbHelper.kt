package co.edu.unal.softwaredirectory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import co.edu.unal.softwaredirectory.data.DirectoryContract.Companies
import co.edu.unal.softwaredirectory.data.DirectoryContract.CompanyClassifications

/**
 * Crea y versiona la base `directory.db` (el SQLiteOpenHelper del tutorial del enunciado).
 *
 * @param name null = base en memoria (tests).
 * @param seed insertar [SampleData] al crear la base, o sea solo la primera vez que se abre la app.
 */
class DirectoryDbHelper(
    context: Context,
    name: String? = DATABASE_NAME,
    private val seed: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis
) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "directory.db"
        const val DATABASE_VERSION = 1

        private val CLASSIFICATION_CODES = Classification.entries.joinToString(", ") { "'${it.code}'" }

        private val SQL_CREATE_COMPANIES = """
            CREATE TABLE ${Companies.TABLE} (
                ${Companies.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Companies.NAME} TEXT NOT NULL CHECK (length(trim(${Companies.NAME})) > 0),
                ${Companies.NAME_KEY} TEXT NOT NULL UNIQUE,
                ${Companies.WEBSITE} TEXT,
                ${Companies.PHONE} TEXT,
                ${Companies.EMAIL} TEXT,
                ${Companies.PRODUCTS} TEXT,
                ${Companies.CREATED_AT} INTEGER NOT NULL,
                ${Companies.UPDATED_AT} INTEGER NOT NULL
            )
        """.trimIndent()

        // ON DELETE CASCADE: borrar la empresa borra sus clasificaciones (requiere las FK activas, ver onConfigure)
        private val SQL_CREATE_CLASSIFICATIONS = """
            CREATE TABLE ${CompanyClassifications.TABLE} (
                ${CompanyClassifications.COMPANY_ID} INTEGER NOT NULL
                    REFERENCES ${Companies.TABLE}(${Companies.ID}) ON DELETE CASCADE,
                ${CompanyClassifications.CLASSIFICATION} TEXT NOT NULL
                    CHECK (${CompanyClassifications.CLASSIFICATION} IN ($CLASSIFICATION_CODES)),
                PRIMARY KEY (${CompanyClassifications.COMPANY_ID}, ${CompanyClassifications.CLASSIFICATION})
            )
        """.trimIndent()
    }

    /** SQLite trae las claves foráneas apagadas por defecto: sin esto el ON DELETE CASCADE no hace nada. */
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    /** Corre dentro de una transacción (la abre SQLiteOpenHelper): o queda todo creado o nada. */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_COMPANIES)
        db.execSQL(SQL_CREATE_CLASSIFICATIONS)
        if (seed) SampleData.insertInto(db, clock())
    }

    /**
     * Versión 1: todavía no hay nada que migrar. Cuando cambie el esquema, cada versión agrega su paso
     * (`if (oldVersion < 2) db.execSQL("ALTER TABLE …")`) en vez del DROP TABLE + onCreate del tutorial,
     * que borraría las empresas del usuario.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
