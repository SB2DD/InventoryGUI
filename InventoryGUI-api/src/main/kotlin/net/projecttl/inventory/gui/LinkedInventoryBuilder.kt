package net.projecttl.inventory.gui

import net.kyori.adventure.text.Component
import net.projecttl.inventory.InventoryGUI.inventoryIds
import net.projecttl.inventory.InventoryGUI.plugin
import net.projecttl.inventory.util.*
import net.projecttl.inventory.util.InventoryType
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * build 함수 실행 후에도 인벤토리를 수정할 수 있는 InventoryBuilder 입니다.
 */
data class LinkedInventoryBuilder(
    override val player: Player,
    override val slotType: InventoryType,
    override val title: Component
): Listener, InventoryBuilder {
    override val slots = ObservableHashMap<Int, LinkedSlot>()
    override val id: UUID = UUID.randomUUID()
    override lateinit var inventory: Inventory
        private set
        
    val closeHandlers = ArrayList<InventoryCloseEvent.() -> Unit>()

    init {
        inventoryIds[id] = this
    }

    override fun slot(slot: Int, item: ItemStack, handler: InventoryClickEvent.() -> Unit) {
        DupePrevention.setInventoryItem(item)
        slots[slot] = LinkedSlot(item, SlotHandler().apply { onClick(handler) })
    }
    
    override fun onClose(handler: InventoryCloseEvent.() -> Unit) {
        closeHandlers.add(handler)
    }

    override fun slot(slot: Int, item: ItemStack) {
        slot(slot, item) {}
    }

    override fun close() {
        if (this::inventory.isInitialized)
            inventory.close()
    }

    override fun build(): Inventory {
        inventory = Bukkit.createInventory(null, slotType.size, title)

        for (slot in slots.entries) {
            inventory.setItem(slot.key, slot.value.stack)
        }

        slots.addObserver {
            DupePrevention.setInventoryItem(it.second.stack)
            inventory.setItem(it.first, it.second.stack)
        }
        DupePrevention.tryRegisterEvents()
        player.openInventory(inventory)
        Bukkit.getServer().pluginManager.registerEvents(this, plugin)
        return inventory
    }

    override fun destroy() {

    }

    @EventHandler
    private fun InventoryClickEvent.listener() {
        if (title.compareTo(this.view.title())) {
            if (inventoryIds.contains(id) && this.currentItem != null && view.player == this@LinkedInventoryBuilder.player) {
                if (inventory == this@LinkedInventoryBuilder.inventory) {
                    for (slot in slots.entries) {
                        if (slot.key == this.rawSlot){
                            this.isCancelled = true
                            slot.value.handler.click.forEach {
                                it(this)
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private fun InventoryMoveItemEvent.listener2() {
        if (inventoryIds.contains(id) && this.source.holder?.inventory?.viewers?.contains(player)!!
            && this.source.holder is Container && (this.source.holder as Container).customName() == title
        )
            this.isCancelled = true
    }

    @EventHandler
    private fun InventoryCloseEvent.listener3() {
        if (view.player == this@LinkedInventoryBuilder.player && inventoryIds.contains(id)) {
            for(closeHandler in closeHandlers) {
                closeHandler(this)
            }

            for (item in player.inventory) {
                if (DupePrevention.isInventoryItem(item)) {
                    player.inventory.remove(item)
                }
            }

            inventoryIds.remove(id)
            HandlerList.unregisterAll(this@LinkedInventoryBuilder)
        }
    }

    @EventHandler
    private fun PlayerSwapHandItemsEvent.listener4() {
        if (player.inventory == this@LinkedInventoryBuilder.inventory) {
            isCancelled = true
        }
    }
}
