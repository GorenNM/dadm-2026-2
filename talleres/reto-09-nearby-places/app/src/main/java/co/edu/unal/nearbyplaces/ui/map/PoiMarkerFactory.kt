package co.edu.unal.nearbyplaces.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.content.ContextCompat
import co.edu.unal.nearbyplaces.R
import co.edu.unal.nearbyplaces.data.GeoPoint
import co.edu.unal.nearbyplaces.data.Poi
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.max
import kotlin.math.roundToInt

/** Arma los marcadores del mapa: cada lugar con su pin de color y el nombre siempre visible debajo. */
class PoiMarkerFactory(private val context: Context, private val map: MapView) {

    private val mResources = context.resources
    private val mPinSize = mResources.getDimensionPixelSize(R.dimen.poi_pin_size)
    private val mCenterSize = mResources.getDimensionPixelSize(R.dimen.center_marker_size)
    private val mMaxLabelWidth = mResources.getDimension(R.dimen.poi_label_max_width)

    // Un solo par de Paint para todas las etiquetas: el halo blanco las hace legibles sobre cualquier parte del mapa
    private val mLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.map_label_text)
        textSize = mResources.getDimension(R.dimen.poi_label_text_size)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val mHaloPaint = Paint(mLabelPaint).apply {
        color = ContextCompat.getColor(context, R.color.map_label_halo)
        style = Paint.Style.STROKE
        strokeWidth = mResources.displayMetrics.density * 3
        strokeJoin = Paint.Join.ROUND
    }

    fun create(poi: Poi): Marker {
        val pin = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_pin)).mutate().apply {
            setTint(poi.category.color)
        }
        // El nombre largo se recorta con "…": una etiqueta ancha taparía los lugares vecinos
        val label = TextUtils.ellipsize(poi.name, android.text.TextPaint(mLabelPaint), mMaxLabelWidth, TextUtils.TruncateAt.END).toString()
        val icon = LabeledPinDrawable(pin, mPinSize, label, mLabelPaint, mHaloPaint)

        return Marker(map).apply {
            position = org.osmdroid.util.GeoPoint(poi.location.latitude, poi.location.longitude)
            this.icon = icon
            // La punta del pin (abajo de la parte del pin, encima de la etiqueta) queda sobre la coordenada
            setAnchor(Marker.ANCHOR_CENTER, icon.pinTipAnchor)
            title = poi.name
            snippet = snippetOf(poi)
        }
    }

    /** La marca del centro de la búsqueda (la posición del usuario o el punto de la pulsación larga). */
    fun createCenter(center: GeoPoint): Marker {
        val dot = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_center)).mutate()
        dot.setBounds(0, 0, mCenterSize, mCenterSize)
        return Marker(map).apply {
            position = org.osmdroid.util.GeoPoint(center.latitude, center.longitude)
            icon = dot
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = context.getString(R.string.map_center_title)
        }
    }

    /** "Hospitales y clínicas · 1,2 km", y la dirección debajo si OpenStreetMap la trae. */
    private fun snippetOf(poi: Poi): String {
        val first = context.getString(
            R.string.poi_snippet, context.getString(poi.category.labelRes), formatDistance(poi.distanceMeters)
        )
        return if (poi.address == null) first else first + "\n" + poi.address
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) context.getString(R.string.distance_meters, meters.roundToInt())
        else context.getString(R.string.distance_kilometers, meters / 1000)
}

/**
 * El pin de [pin] arriba y [label] centrada debajo, dibujados al vuelo (sin crear un bitmap por lugar: con 300
 * lugares serían decenas de MB). El ancho es el del texto si es más ancho que el pin.
 */
private class LabeledPinDrawable(
    private val pin: Drawable,
    private val pinSize: Int,
    private val label: String,
    private val labelPaint: Paint,
    private val haloPaint: Paint
) : Drawable() {

    private val mLabelWidth = labelPaint.measureText(label)
    private val mFontMetrics = labelPaint.fontMetrics
    private val mLabelHeight = (mFontMetrics.descent - mFontMetrics.ascent).roundToInt()
    private val mWidth = max(pinSize.toFloat(), mLabelWidth).roundToInt() + 2
    private val mHeight = pinSize + mLabelHeight

    /** Qué fracción de la altura total queda por encima de la punta del pin. */
    val pinTipAnchor: Float = pinSize.toFloat() / mHeight

    override fun getIntrinsicWidth() = mWidth
    override fun getIntrinsicHeight() = mHeight

    override fun draw(canvas: Canvas) {
        val left = bounds.left
        val top = bounds.top
        val centerX = left + bounds.width() / 2f

        val pinLeft = (centerX - pinSize / 2f).roundToInt()
        pin.setBounds(pinLeft, top, pinLeft + pinSize, top + pinSize)
        pin.draw(canvas)

        val baseline = top + pinSize - mFontMetrics.ascent
        canvas.drawText(label, centerX, baseline, haloPaint)
        canvas.drawText(label, centerX, baseline, labelPaint)
    }

    override fun setAlpha(alpha: Int) {
        pin.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pin.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
