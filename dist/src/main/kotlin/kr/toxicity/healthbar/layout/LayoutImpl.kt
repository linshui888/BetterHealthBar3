package kr.toxicity.healthbar.layout

import kr.toxicity.healthbar.api.condition.HealthBarCondition
import kr.toxicity.healthbar.api.condition.HealthBarOperation
import kr.toxicity.healthbar.api.layout.Layout
import kr.toxicity.healthbar.util.forEachSubConfiguration
import org.bukkit.configuration.ConfigurationSection

abstract class LayoutImpl(
    layer: Int,
    section: ConfigurationSection
): Layout {
    private val x = section.getInt("x")
    private val y = section.getInt("y")
    private val groupX = section.getInt("group-x")
    private val groupY = section.getInt("group-y")
    private val scale = section.getDouble("scale", 1.0).apply {
        if (this <= 0) throw RuntimeException("Scale cannot be <= 0.")
    }
    private val layer = section.getInt("layer", layer).coerceAtLeast(1).coerceAtMost(254)
    private val condition = run {
        var condition = HealthBarCondition.TRUE
        section.getConfigurationSection("conditions")?.forEachSubConfiguration { _, configurationSection ->
            val old = condition
            var get = HealthBarOperation.of(configurationSection)
            if (configurationSection.getBoolean("not")) get = get.not()
            condition = when (val gate = configurationSection.getString("gate")?.lowercase() ?: "and") {
                "and" -> HealthBarCondition {
                    old.apply(it) && get.apply(it)
                }
                "or" -> HealthBarCondition {
                    old.apply(it) || get.apply(it)
                }
                else -> throw RuntimeException("Unsupported gate: $gate")
            }
        }
        condition
    }

    override fun layer(): Int = layer
    override fun x(): Int = x
    override fun y(): Int = y
    override fun groupX(): Int = groupX
    override fun groupY(): Int = groupY
    override fun scale(): Double = scale
    override fun condition(): HealthBarCondition = condition
}