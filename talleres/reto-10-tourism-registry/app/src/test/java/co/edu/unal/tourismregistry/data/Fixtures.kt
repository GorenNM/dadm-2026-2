package co.edu.unal.tourismregistry.data

/** Respuestas reales del servicio (recortadas), guardadas en src/test/resources. */
object Fixtures {
    fun load(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource(name)) { "missing fixture $name" }
            .readText(Charsets.UTF_8)
}
