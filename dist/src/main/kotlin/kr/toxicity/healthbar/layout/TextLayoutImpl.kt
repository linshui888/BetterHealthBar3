package kr.toxicity.healthbar.layout

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kr.toxicity.healthbar.api.component.PixelComponent
import kr.toxicity.healthbar.api.component.WidthComponent
import kr.toxicity.healthbar.api.healthbar.HealthBarPair
import kr.toxicity.healthbar.api.layout.TextLayout
import kr.toxicity.healthbar.api.placeholder.PlaceholderContainer
import kr.toxicity.healthbar.api.renderer.TextRenderer
import kr.toxicity.healthbar.api.text.TextAlign
import kr.toxicity.healthbar.data.BitmapData
import kr.toxicity.healthbar.manager.TextManagerImpl
import kr.toxicity.healthbar.pack.PackResource
import kr.toxicity.healthbar.util.*
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.configuration.ConfigurationSection
import java.util.Collections
import java.util.function.Function
import kotlin.math.roundToInt

class TextLayoutImpl(
    private val parent: LayoutGroupImpl,
    private val name: String,
    layer: Int,
    section: ConfigurationSection
): TextLayout, LayoutImpl(layer, section) {
    companion object {
        private val defaultWidth = WidthKey(Key.key("minecraft", "default"), 0)
    }
    private val text = section.getString("text").ifNull("Unable to find 'text' configuration.").run {
        TextManagerImpl.text(this).ifNull("Unable to find this text: $this")
    }
    private val height = (text.height().toDouble() * scale()).roundToInt().toHeight()
    private val textWidth = Collections.unmodifiableMap(HashMap<Char, Int>().apply {
        val div = height.toDouble() / text.height().toDouble()
        text.chatWidth().forEach {
            put(it.key, (it.value * div).roundToInt())
        }
    })
    private val align = section.getString("align").ifNull("Unable to find 'align' command.").run {
        TextAlign.valueOf(uppercase())
    }
    private val duration = section.getInt("duration", - 1)
    private val keys = ArrayList<WidthKey>()
    private val pattern = PlaceholderContainer.toString(section.getString("pattern").ifNull("Unable to find 'pattern' command."))

    override fun charWidth(): Map<Char, Int> = textWidth
    override fun align(): TextAlign = align
    override fun pattern(): Function<HealthBarPair, String> = pattern

    private class WidthKey(
        val key: Key,
        val x: Int,
    )

    fun build(resource: PackResource, count: Int) {
        val dataList = ArrayList<JsonData>()
        val fileParent = "${parent.name}/text/${layer()}"
        text.bitmap().forEachIndexed { index, textBitmap ->
            val fileName = "$fileParent/${index + 1}.png"
            dataList.add(JsonData(
                "$NAMESPACE:$fileName",
                textBitmap.array
            ))
            resource.textures.add(fileName) {
                textBitmap.image.withOpacity(layer()).toByteArray()
            }
        }

        val map = HashMap<BitmapData, WidthKey>()
        for (i in (0..<count)) {
            val y = y() + groupY() * i
            val keyName = "${parent.name}/$name/${i + 1}"
            keys.add(map.computeIfAbsent(BitmapData(keyName, y, height)) {
                resource.font.add("$keyName.json") {
                    JsonObject().apply {
                        add("providers", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("type", "space")
                                add("advances", JsonObject().apply {
                                    addProperty(" ", 4)
                                })
                            })
                            dataList.forEach {
                                add(JsonObject().apply {
                                    addProperty("type", "bitmap")
                                    addProperty("file", it.file)
                                    addProperty("ascent", y.toAscent())
                                    addProperty("height", height)
                                    add("chars", it.chars)
                                })
                            }
                        })
                    }.save()
                }
                WidthKey(Key.key(NAMESPACE, keyName), x() + groupX() * i)
            })
        }
    }

    override fun createRenderer(pair: HealthBarPair): TextRenderer {
        return Renderer(pair)
    }

    private inner class Renderer(
        private val pair: HealthBarPair
    ): TextRenderer {
        private var d = 0
        override fun hasNext(): Boolean {
            return duration < 0 || ++d <= duration
        }

        override fun canRender(): Boolean {
            return condition().apply(pair)
        }

        override fun render(groupCount: Int): PixelComponent {
            val key = if (keys.isNotEmpty()) keys[groupCount.coerceAtMost(keys.lastIndex)] else defaultWidth
            val string = pattern.apply(pair)
            val component = WidthComponent(
                string.sumOf {
                    if (it == ' ') 4 else (textWidth[it] ?: 0) + 1
                },
                Component.text().font(key.key).content(string)
            )
            return component.toPixelComponent(key.x + when (align) {
                TextAlign.LEFT -> 0
                TextAlign.CENTER -> -component.width / 2
                TextAlign.RIGHT -> -component.width
            })
        }

    }

    private class JsonData(
        val file: String,
        val chars: JsonArray
    )
}