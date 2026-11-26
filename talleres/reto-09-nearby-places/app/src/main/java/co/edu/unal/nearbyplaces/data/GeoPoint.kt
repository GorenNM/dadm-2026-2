package co.edu.unal.nearbyplaces.data

/** Un punto de la Tierra. Propio (no el de osmdroid) para que la lógica no dependa del mapa. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Rectángulo en coordenadas que rodea un círculo; el mapa lo usa para encuadrar el radio. */
data class GeoBounds(val north: Double, val south: Double, val east: Double, val west: Double)
