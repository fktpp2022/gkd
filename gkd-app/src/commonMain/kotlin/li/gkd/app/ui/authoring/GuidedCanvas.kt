package li.gkd.app.ui.authoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import li.gkd.app.authoring.GuidedNodeLabel
import li.gkd.app.authoring.isUsableNode
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.NodeInfo
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
fun GuidedRuleCanvas(
    snapshot: ComplexSnapshot,
    screenshot: ByteArray?,
    matches: List<NodeInfo>,
    actableId: Int?,
    selectedId: Int?,
    description: String,
    modifier: Modifier = Modifier,
    onCandidates: ((List<NodeInfo>) -> Unit)? = null,
) {
    val image = remember(screenshot) {
        screenshot?.let { bytes ->
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        }
    }
    val outlineNodes = remember(snapshot) {
        snapshot.nodes.filter {
            it.isUsableNode() && (it.attr.clickable || GuidedNodeLabel.of(it) != null)
        }
    }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val matchColor = MaterialTheme.colorScheme.primary
    val actableColor = MaterialTheme.colorScheme.tertiary
    val selectColor = MaterialTheme.colorScheme.secondary
    val contrast = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)

    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .onSizeChanged { canvasSize = it }
            .semantics { contentDescription = description }
            .pointerInput(snapshot, onCandidates != null) {
                if (onCandidates == null) return@pointerInput
                detectTapGestures { position ->
                    val size = Size(
                        canvasSize.width.toFloat(),
                        canvasSize.height.toFloat(),
                    )
                    val geometry = canvasGeometry(size, snapshot, zoom, pan)
                    val point = (position - geometry.translate) / geometry.scale
                    if (
                        point.x < 0f || point.y < 0f ||
                        point.x > snapshot.screenWidth || point.y > snapshot.screenHeight
                    ) {
                        return@detectTapGestures
                    }
                    val candidates = candidatesAt(snapshot.nodes, point)
                    if (candidates.isNotEmpty()) onCandidates(candidates)
                }
            }
            .pointerInput(snapshot) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val size = Size(
                        canvasSize.width.toFloat(),
                        canvasSize.height.toFloat(),
                    )
                    if (size.width <= 0f || size.height <= 0f) return@detectTransformGestures
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ratio = newZoom / zoom
                    val nextPan =
                        centroid - center - (centroid - center - pan) * ratio + panChange
                    zoom = newZoom
                    pan = if (newZoom <= 1f) Offset.Zero else nextPan
                }
            },
    ) {
        val size = Size(this.size.width.toFloat(), this.size.height.toFloat())
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val geometry = canvasGeometry(size, snapshot, zoom, pan)
        val scale = geometry.scale
        withTransform({
            translate(geometry.translate.x, geometry.translate.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            if (image != null) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(snapshot.screenWidth, snapshot.screenHeight),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                drawRect(background)
                val step = 64f
                var x = 0f
                while (x <= snapshot.screenWidth) {
                    drawLine(
                        color = grid,
                        start = Offset(x, 0f),
                        end = Offset(x, snapshot.screenHeight.toFloat()),
                        strokeWidth = 1.dp.toPx() / scale,
                    )
                    x += step
                }
                var y = 0f
                while (y <= snapshot.screenHeight) {
                    drawLine(
                        color = grid,
                        start = Offset(0f, y),
                        end = Offset(snapshot.screenWidth.toFloat(), y),
                        strokeWidth = 1.dp.toPx() / scale,
                    )
                    y += step
                }
                outlineNodes.forEach { node ->
                    drawRect(
                        color = outline,
                        topLeft = node.rect().topLeft,
                        size = node.rect().size,
                        style = Stroke(width = 1.dp.toPx() / scale),
                    )
                }
            }
            val matchStroke = 3.dp.toPx() / scale
            matches.forEach { node ->
                val rect = node.rect()
                drawRect(
                    color = contrast,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = matchStroke + 2.dp.toPx() / scale),
                )
                drawRect(
                    color = matchColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = matchStroke),
                )
            }
            if (selectedId != null) {
                snapshot.nodes.firstOrNull { it.id == selectedId }?.let { node ->
                    drawRect(
                        color = selectColor,
                        topLeft = node.rect().topLeft,
                        size = node.rect().size,
                        style = Stroke(
                            width = 2.dp.toPx() / scale,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx() / scale, 6.dp.toPx() / scale),
                            ),
                        ),
                    )
                }
            }
            if (actableId != null) {
                snapshot.nodes.firstOrNull { it.id == actableId }?.let { node ->
                    val rect = node.rect()
                    drawRect(
                        color = contrast,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 6.dp.toPx() / scale),
                    )
                    drawRect(
                        color = actableColor,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 3.dp.toPx() / scale),
                    )
                    val radius = 4.dp.toPx() / scale
                    listOf(
                        rect.topLeft,
                        Offset(rect.right, rect.top),
                        Offset(rect.left, rect.bottom),
                        rect.bottomRight,
                    ).forEach { corner ->
                        drawCircle(color = actableColor, radius = radius, center = corner)
                    }
                }
            }
        }
    }
}

private data class CanvasGeometry(val scale: Float, val translate: Offset)

private fun canvasGeometry(
    size: Size,
    snapshot: ComplexSnapshot,
    zoom: Float,
    pan: Offset,
): CanvasGeometry {
    if (snapshot.screenWidth <= 0 || snapshot.screenHeight <= 0) {
        return CanvasGeometry(1f, Offset.Zero)
    }
    val baseScale = min(
        size.width / snapshot.screenWidth,
        size.height / snapshot.screenHeight,
    )
    val baseOffset = Offset(
        (size.width - snapshot.screenWidth * baseScale) / 2f,
        (size.height - snapshot.screenHeight * baseScale) / 2f,
    )
    val scale = baseScale * zoom
    val center = Offset(size.width / 2f, size.height / 2f)
    val translate = baseOffset * zoom + center * (1f - zoom) + pan
    return CanvasGeometry(scale, translate)
}

private fun candidatesAt(nodes: List<NodeInfo>, point: Offset): List<NodeInfo> {
    val hits = nodes.filter { node ->
        val attr = node.attr
        node.isUsableNode() &&
                point.x >= attr.left &&
                point.x <= attr.right &&
                point.y >= attr.top &&
                point.y <= attr.bottom
    }
    if (hits.isEmpty()) return emptyList()
    val meaningful = hits.filter { it.attr.clickable || GuidedNodeLabel.of(it) != null }
    val base = meaningful.ifEmpty { hits }
    return base.filter { candidate ->
        base.none { other ->
            other.id != candidate.id &&
                    other.area() < candidate.area() &&
                    other.contains(candidate)
        }
    }.sortedWith(compareBy<NodeInfo>({ it.area() }, { -it.attr.depth }))
}

private fun NodeInfo.area(): Long = attr.width.toLong() * attr.height.toLong()

private fun NodeInfo.contains(other: NodeInfo): Boolean {
    val a = attr
    val b = other.attr
    return a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom
}

private fun NodeInfo.rect(): Rect = Rect(
    left = attr.left.toFloat(),
    top = attr.top.toFloat(),
    right = attr.right.toFloat(),
    bottom = attr.bottom.toFloat(),
)

@Composable
fun guidedNodeLabel(node: NodeInfo): String {
    val label = displayLabel(node)
    return label ?: stringResource(Res.string.guided_node_unnamed)
}

private fun displayLabel(node: NodeInfo): String? =
    GuidedNodeLabel.of(node)?.let(::displayLabel)

fun displayLabel(value: String): String? =
    value.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }

@Composable
fun guidedNodeMeta(node: NodeInfo): String {
    val type = node.attr.name?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.guided_node_unnamed)
    return stringResource(
        Res.string.guided_node_meta,
        type,
        node.attr.width.toString(),
        node.attr.height.toString(),
    )
}
