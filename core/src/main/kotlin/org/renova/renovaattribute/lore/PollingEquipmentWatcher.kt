package org.renova.renovaattribute.lore

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseArmorEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.renova.renovaattribute.RenovaAttribute
import org.renova.renovaattribute.platform.EquipmentWatcher
import java.util.UUID

/**
 * Equipment detection for servers without Paper's EntityEquipmentChangedEvent. Inventory and item
 * events cover what players do themselves; a periodic lore fingerprint of online players catches
 * equipment that other plugins change through the API.
 */
class PollingEquipmentWatcher : EquipmentWatcher {
    override val name: String = "polling"
    private val fingerprints = HashMap<UUID, Int>()
    private var pollTask: BukkitTask? = null

    override fun configure(pollIntervalTicks: Long) {
        shutdown()
        if (pollIntervalTicks > 0) {
            pollTask = RenovaAttribute.instance.server.scheduler.runTaskTimer(
                RenovaAttribute.instance,
                Runnable(::poll),
                pollIntervalTicks,
                pollIntervalTicks,
            )
        }
    }

    override fun shutdown() {
        pollTask?.cancel()
        pollTask = null
        fingerprints.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) = schedule(event.whoClicked)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) = schedule(event.whoClicked)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) = schedule(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) = schedule(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) = schedule(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) = schedule(event.entity)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemBreak(event: PlayerItemBreakEvent) = schedule(event.player)

    /** Right-clicking air arrives already cancelled, yet still equips armour. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.item != null && (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK)) {
            schedule(event.player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDispenseArmor(event: BlockDispenseArmorEvent) = schedule(event.targetEntity)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onResurrect(event: EntityResurrectEvent) = schedule(event.entity)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        fingerprints.remove(event.player.uniqueId)
    }

    private fun schedule(entity: Entity) {
        (entity as? LivingEntity)?.let(EquipmentRefreshScheduler::schedule)
    }

    private fun poll() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val current = fingerprint(player)
            val previous = fingerprints.put(player.uniqueId, current)
            if (previous != null && previous != current) {
                EquipmentRefreshScheduler.schedule(player)
            }
        }
    }

    /** Equipment attributes come from lore alone, so only lore changes need a refresh. */
    private fun fingerprint(player: Player): Int {
        val inventory = player.inventory
        var hash = loreHash(inventory.itemInMainHand)
        hash = 31 * hash + loreHash(inventory.itemInOffHand)
        inventory.armorContents.forEach { hash = 31 * hash + loreHash(it) }
        return hash
    }

    private fun loreHash(item: ItemStack?): Int {
        if (item == null || !item.hasItemMeta()) {
            return 0
        }
        return item.itemMeta?.lore?.hashCode() ?: 0
    }
}
