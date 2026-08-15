package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val DailyPicksCookieShape: Shape = expressiveRadialShape(
    lobeCount = 8,
    innerRadius = 0.86f,
)

internal val DailyPicksPebbleShape: Shape = roundedPolygonShape(
    vertexCount = 7,
    cornerCutFraction = 0.28f,
)

private fun expressiveRadialShape(
    lobeCount: Int,
    innerRadius: Float,
): Shape = GenericShape { size, _ ->
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val scale = minOf(centerX, centerY)
    val sampleCount = lobeCount * 24

    repeat(sampleCount) { index ->
        val angle = index.toFloat() / sampleCount * (2f * PI.toFloat())
        val radiusProgress = (cos(lobeCount * angle) + 1f) / 2f
        val radius = innerRadius + (1f - innerRadius) * radiusProgress
        val x = centerX + cos(angle) * scale * radius
        val y = centerY + sin(angle) * scale * radius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun roundedPolygonShape(
    vertexCount: Int,
    cornerCutFraction: Float,
): Shape = GenericShape { size, _ ->
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val scale = minOf(centerX, centerY)
    val vertices = List(vertexCount) { index ->
        val angle = index.toFloat() / vertexCount * (2f * PI.toFloat())
        Offset(
            x = centerX + cos(angle) * scale,
            y = centerY + sin(angle) * scale,
        )
    }

    fun cutPoint(vertex: Offset, neighbor: Offset): Offset = Offset(
        x = vertex.x + (neighbor.x - vertex.x) * cornerCutFraction,
        y = vertex.y + (neighbor.y - vertex.y) * cornerCutFraction,
    )

    val firstVertex = vertices.first()
    val firstStart = cutPoint(firstVertex, vertices.last())
    moveTo(firstStart.x, firstStart.y)
    vertices.forEachIndexed { index, vertex ->
        val previous = vertices[(index - 1 + vertexCount) % vertexCount]
        val next = vertices[(index + 1) % vertexCount]
        val start = cutPoint(vertex, previous)
        val end = cutPoint(vertex, next)
        lineTo(start.x, start.y)
        quadraticTo(vertex.x, vertex.y, end.x, end.y)
    }
    close()
}
