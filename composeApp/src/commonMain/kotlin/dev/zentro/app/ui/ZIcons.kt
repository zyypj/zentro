package dev.zentro.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Alguns ícones do Material que não estão no pacote "core". Definidos aqui para não precisar
 * do pacote estendido inteiro (dezenas de MB) e manter o app leve.
 */
object ZIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    val Wallet = icon(
        "Wallet",
        "M21,18v1c0,1.1 -0.9,2 -2,2L5,21c-1.11,0 -2,-0.9 -2,-2L3,5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10L22,8L12,8v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    )
    val Alarm = icon(
        "Alarm",
        "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM12.5,8L11,8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
    )
    val Repeat = icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val Sync = icon(
        "Sync",
        "M12,4L12,1L8,5l4,4L12,6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
    )
    val TrendingUp = icon("TrendingUp", "M16,6l2.29,2.29 -4.88,4.88 -4,-4L2,16.59 3.41,18l6,-6 4,4 6.3,-6.29L22,12V6z")
    val TrendingDown = icon("TrendingDown", "M16,18l2.29,-2.29 -4.88,-4.88 -4,4L2,7.41 3.41,6l6,6 4,-4 6.3,6.29L22,12v6z")
    val Eye = icon(
        "Eye",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
    )
    val FindReplace = icon(
        "FindReplace",
        "M11,6c1.38,0 2.63,0.56 3.54,1.46L12,10h6V4l-2.05,2.05C14.68,4.78 12.93,4 11,4c-3.53,0 -6.43,2.61 -6.92,6H6.1c0.46,-2.28 2.48,-4 4.9,-4zM16.64,15.14c0.66,-0.9 1.12,-1.97 1.28,-3.14H15.9c-0.46,2.28 -2.48,4 -4.9,4 -1.38,0 -2.63,-0.56 -3.54,-1.46L10,12H4v6l2.05,-2.05C7.32,17.22 9.07,18 11,18c1.55,0 2.98,-0.51 4.14,-1.36L20,21.49 21.49,20l-4.85,-4.86z",
    )
    val CreditCard = icon(
        "CreditCard",
        "M20,4H4c-1.11,0 -1.99,0.89 -1.99,2L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V6c0,-1.11 -0.89,-2 -2,-2zM20,18H4v-6h16v6zM20,8H4V6h16v2z",
    )
    val Notes = icon(
        "Notes",
        "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z",
    )
}
