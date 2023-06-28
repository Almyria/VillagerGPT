package tj.horner.villagergpt.handlers

import com.aallam.openai.api.BetaOpenAI
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import tj.horner.villagergpt.MetadataKey
import tj.horner.villagergpt.VillagerGPT
import tj.horner.villagergpt.chat.ChatMessageTemplate
import tj.horner.villagergpt.conversation.formatting.MessageFormatter
import tj.horner.villagergpt.events.VillagerConversationEndEvent
import tj.horner.villagergpt.events.VillagerConversationMessageEvent
import tj.horner.villagergpt.events.VillagerConversationStartEvent

class ConversationEventsHandler(private val plugin: VillagerGPT) : Listener {
    @EventHandler
    fun onConversationStart(evt: VillagerConversationStartEvent) {
        val message = Component.text("Vous êtes maintenant en conversation avec ")
            .append(evt.conversation.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text(". Envoyez un message de chat pour commencer et utilisez /ttvend pour y mettre fin."))
            .decorate(TextDecoration.ITALIC)

        evt.conversation.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        evt.conversation.villager.isAware = false
        evt.conversation.villager.lookAt(evt.conversation.player)

        plugin.logger.info("La conversation s'est engagée entre ${evt.conversation.player.name} et ${evt.conversation.villager.name}")
    }

    @EventHandler
    fun onConversationEnd(evt: VillagerConversationEndEvent) {
        val message = Component.text("Votre conversation avec ")
            .append(evt.villager.name().color(NamedTextColor.AQUA))
            .append(Component.text(" est terminée"))
            .decorate(TextDecoration.ITALIC)

        evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))

        evt.villager.resetOffers()
        evt.villager.isAware = true

        plugin.logger.info("La conversation s'est terminée entre ${evt.player.name} et ${evt.villager.name}")
    }

    @EventHandler
    fun onVillagerInteracted(evt: PlayerInteractEntityEvent) {
        if (evt.rightClicked !is Villager) return
        val villager = evt.rightClicked as Villager

        // Villager is in a conversation with another player
        val existingConversation = plugin.conversationManager.getConversation(villager)
        if (existingConversation != null && existingConversation.player.uniqueId != evt.player.uniqueId) {
            val message = Component.text("Ce villageois est en conversation avec ")
                .append(existingConversation.player.displayName())
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            evt.isCancelled = true
            return
        }

        if (!evt.player.hasMetadata(MetadataKey.SelectingVillager)) return

        // Player is selecting a villager for conversation
        evt.isCancelled = true

        if (villager.profession == Villager.Profession.NONE) {
            val message = Component.text("Vous ne pouvez parler aux villageois que s'ils ont une profession.")
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        plugin.conversationManager.startConversation(evt.player, villager)
        evt.player.removeMetadata(MetadataKey.SelectingVillager, plugin)
    }

    @EventHandler
    suspend fun onSendMessage(evt: AsyncChatEvent) {
        val conversation = plugin.conversationManager.getConversation(evt.player) ?: return
        evt.isCancelled = true

        if (conversation.pendingResponse) {
            val message = Component.text("Veuillez attendre ")
                .append(conversation.villager.name().color(NamedTextColor.AQUA))
                .append(Component.text(" pour répondre"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            return
        }

        conversation.pendingResponse = true
        val villager = conversation.villager

        try {
            val pipeline = plugin.messagePipeline

            val playerMessage = PlainTextComponentSerializer.plainText().serialize(evt.originalMessage())
            val formattedPlayerMessage = MessageFormatter.formatMessageFromPlayer(Component.text(playerMessage), villager)

            evt.player.sendMessage(formattedPlayerMessage)

            val actions = pipeline.run(playerMessage, conversation)
            if (!conversation.ended) {
                withContext(plugin.minecraftDispatcher) {
                    actions.forEach { it.run() }
                }
            }
        } catch(e: Exception) {
            val message = Component.text("Un problème s'est produit lors de la réponse de ")
                .append(villager.name().color(NamedTextColor.AQUA))
                .append(Component.text(". Réessaies plus tard"))
                .decorate(TextDecoration.ITALIC)

            evt.player.sendMessage(ChatMessageTemplate.withPluginNamePrefix(message))
            throw(e)
        } finally {
            conversation.pendingResponse = false
        }
    }

    @OptIn(BetaOpenAI::class)
    @EventHandler
    fun onConversationMessage(evt: VillagerConversationMessageEvent) {
        if (!plugin.config.getBoolean("log-conversations")) return
        plugin.logger.info("Messages entre ${evt.conversation.player.name} et ${evt.conversation.villager.name}: ${evt.message}")
    }

    @EventHandler
    fun onVillagerDied(evt: EntityDeathEvent) {
        if (evt.entity !is Villager) return
        val villager = evt.entity as Villager

        val conversation = plugin.conversationManager.getConversation(villager)
        if (conversation != null) {
            plugin.conversationManager.endConversation(conversation)
        }
    }
}