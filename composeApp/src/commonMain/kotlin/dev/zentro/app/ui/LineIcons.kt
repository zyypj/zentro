package dev.zentro.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ícones de traço fino (1,7) da identidade visual, desenhados para o Zentro numa grade 24×24.
 * A cor vem do `tint` do Icon, como nos ícones do Material.
 */
object Line {
    private fun icon(name: String, vararg paths: String, width: Float = 1.7f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun gearPath(): String {
        val points = buildList {
            for (i in 0 until 8) {
                val a = i * PI / 4
                listOf(-0.36 to 7.2, -0.2 to 9.3, 0.2 to 9.3, 0.36 to 7.2).forEach { (da, r) ->
                    add(12 + r * cos(a + da) to 12 + r * sin(a + da))
                }
            }
        }
        fun f(v: Double) = ((v * 100).toLong() / 100.0).toString()
        return points.mapIndexed { i, (x, y) -> (if (i == 0) "M" else "L") + f(x) + " " + f(y) }.joinToString(" ") + "Z"
    }

    val Home = icon("Home", "M4 10.2 12 4l8 6.2V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1z")
    val Calendar = icon("Calendar", "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 10h16", "M8.5 3v4", "M15.5 3v4")
    val Note = icon("Note", "M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-10z", "M14 3.5v5h5", "M8.5 13h7", "M8.5 16.5h5")
    val Wallet = icon("Wallet", "M4 7a2 2 0 0 1 2-2h11a1 1 0 0 1 1 1v3", "M4 7v10a2 2 0 0 0 2 2h13a1 1 0 0 0 1-1v-3", "M20 9.5h-3.5a2.5 2.5 0 0 0 0 5H20z")
    val Chart = icon("Chart", "M6 19v-5", "M10.5 19V9", "M15 19v-7", "M19.5 19V5")
    val Settings = icon("Settings", gearPath(), "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val Search = icon("Search", "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z", "M15.5 15.5 20 20")
    val Bell = icon("Bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Plus = icon("Plus", "M12 5v14", "M5 12h14", width = 2f)
    val ChevronRight = icon("ChevronRight", "M9.5 6l6 6-6 6")
    val ChevronLeft = icon("ChevronLeft", "M14.5 6l-6 6 6 6")
    val ChevronDown = icon("ChevronDown", "M6 9.5l6 6 6-6")
    val ArrowRight = icon("ArrowRight", "M5 12h14", "M13.5 6.5 19 12l-5.5 5.5")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5", width = 2f)
    val Close = icon("Close", "M6.5 6.5l11 11", "M17.5 6.5l-11 11")
    val Folder = icon("Folder", "M3.5 7.5a2 2 0 0 1 2-2h4l2 2.2h7a2 2 0 0 1 2 2v7.8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")
    val Bulb = icon("Bulb", "M9.5 18h5", "M10.5 21h3", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2v.1h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z")
    val Pin = icon("Pin", "M9 4h6l-1 5 3 3v1.5H7V12l3-3z", "M12 13.5V20")
    val Briefcase = icon("Briefcase", "M4 8.5A1.5 1.5 0 0 1 5.5 7h13A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z", "M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7", "M4 12.5h16")
    val Graduation = icon("Graduation", "M2.5 9.5 12 5l9.5 4.5L12 14z", "M6.5 11.5v4c1.5 1.5 3.5 2.3 5.5 2.3s4-.8 5.5-2.3v-4", "M21.5 9.5V14")
    val User = icon("User", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M5 20a7 7 0 0 1 14 0")
    val Card = icon("Card", "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M3 10h18", "M7 14.5h3")
    val TrendUp = icon("TrendUp", "M4 16l5-5 4 4 7-7", "M15 8h5v5")
    val TrendDown = icon("TrendDown", "M4 8l5 5 4-4 7 7", "M15 16h5v-5")
    val Edit = icon("Edit", "M4.5 19.5h4l10.5-10.5-4-4L4.5 15.5z", "M13.5 6.5l4 4")
    val Trash = icon("Trash", "M4.5 7h15", "M9.5 7V5h5v2", "M6.5 7l1 12.5a1.5 1.5 0 0 0 1.5 1.5h6a1.5 1.5 0 0 0 1.5-1.5L17.5 7")
    val Eye = icon("Eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val Clock = icon("Clock", "M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z", "M12 7.5V12l3 2")
    val Alert = icon("Alert", "M12 4 21 19.5H3z", "M12 10v4", "M12 17v.01")
    val Repeat = icon("Repeat", "M17 3.5l2.5 2.5L17 8.5", "M4.5 11V9.5A3.5 3.5 0 0 1 8 6h11.5", "M7 20.5 4.5 18 7 15.5", "M19.5 13v1.5a3.5 3.5 0 0 1-3.5 3.5H4.5")
    val Layers = icon("Layers", "M12 4 3 8.5l9 4.5 9-4.5z", "M3 12.5l9 4.5 9-4.5", "M3 16.5l9 4.5 9-4.5")
    val Map = icon("Map", "M6 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z", "M7.5 7.2 16.5 17", "M8 6h8")
    val Logout = icon("Logout", "M14 4.5h3.5a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H14", "M10 16.5 14.5 12 10 7.5", "M14.5 12H4")
}
