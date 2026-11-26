package co.edu.unal.softwaredirectory.data

import android.database.sqlite.SQLiteDatabase
import co.edu.unal.softwaredirectory.data.Classification.CONSULTING
import co.edu.unal.softwaredirectory.data.Classification.CUSTOM_DEVELOPMENT
import co.edu.unal.softwaredirectory.data.Classification.SOFTWARE_FACTORY

/**
 * Empresas de ejemplo para la primera vez que se abre la app, así el filtro se puede probar enseguida.
 * Son ficticias: dominios example.com y teléfonos inventados. Hay tildes y "ñ" a propósito, para la búsqueda.
 */
object SampleData {

    val companies = listOf(
        Company(
            name = "Andes Software Factory",
            website = "https://andes-factory.example.com",
            phone = "+57 601 000 0101",
            email = "contacto@andes-factory.example.com",
            products = "Desarrollo de software por lotes, pruebas automatizadas y mantenimiento evolutivo.",
            classifications = setOf(SOFTWARE_FACTORY)
        ),
        Company(
            name = "Consultores Ágiles S.A.S.",
            website = "https://agiles.example.com",
            phone = "+57 300 000 0202",
            email = "hola@agiles.example.com",
            products = "Transformación ágil, arquitectura de software y auditoría de código.",
            classifications = setOf(CONSULTING)
        ),
        Company(
            name = "Código a la Medida Ltda.",
            website = "https://codigomedida.example.com",
            phone = "+57 604 000 0303",
            email = "ventas@codigomedida.example.com",
            products = "Aplicaciones web y móviles a la medida para pymes.",
            classifications = setOf(CUSTOM_DEVELOPMENT)
        ),
        Company(
            name = "Ñandú Tecnología",
            website = "https://nandu.example.com",
            phone = "+57 602 000 0404",
            email = "info@nandu.example.com",
            products = "Consultoría en datos y desarrollo de tableros de analítica.",
            classifications = setOf(CONSULTING, CUSTOM_DEVELOPMENT)
        ),
        Company(
            name = "Pixel & Byte",
            website = "https://pixelbyte.example.com",
            phone = "+57 310 000 0505",
            email = "",
            products = "Apps móviles nativas y equipos de desarrollo dedicados.",
            classifications = setOf(CUSTOM_DEVELOPMENT, SOFTWARE_FACTORY)
        ),
        Company(
            name = "Soluciones Bogotá Cloud",
            website = "",
            phone = "+57 601 000 0606",
            email = "soporte@bogotacloud.example.com",
            products = "Migración a la nube, DevOps y operación de plataformas.",
            classifications = setOf(CONSULTING, SOFTWARE_FACTORY)
        )
    )

    fun insertInto(db: SQLiteDatabase, now: Long) {
        for (company in companies) SqliteCompanyRepository.insertCompany(db, company, now)
    }
}
