package tj.horner.villagergpt.conversation

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.destroystokyo.paper.entity.villager.ReputationType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.plugin.Plugin
import tj.horner.villagergpt.events.VillagerConversationMessageEvent
import java.time.Duration
import java.util.*
import kotlin.random.Random

@OptIn(BetaOpenAI::class)
class VillagerConversation(private val plugin: Plugin, val villager: Villager, val player: Player) {
    private var lastMessageAt: Date = Date()

    val messages = mutableListOf<ChatMessage>()
    var pendingResponse = false
    var ended = false

    init {
        startConversation()
    }

    fun addMessage(message: ChatMessage) {
        val event = VillagerConversationMessageEvent(this, message)
        plugin.server.pluginManager.callEvent(event)

        messages.add(message)
        lastMessageAt = Date()
    }

    fun removeLastMessage() {
        if (messages.size == 0) return
        messages.removeLast()
    }

    fun reset() {
        messages.clear()
        startConversation()
        lastMessageAt = Date()
    }

    fun hasExpired(): Boolean {
        val now = Date()
        val difference = now.time - lastMessageAt.time
        val duration = Duration.ofMillis(difference)
        return duration.toSeconds() > 120
    }

    fun hasPlayerLeft(): Boolean {
        if (player.location.world != villager.location.world) return true

        val radius = 20.0 // blocks?
        val radiusSquared = radius * radius
        val distanceSquared = player.location.distanceSquared(villager.location)
        return distanceSquared > radiusSquared
    }

    private fun startConversation() {
        var messageRole = ChatRole.System
        var prompt = generateSystemPrompt()

        val preambleMessageType = plugin.config.getString("preamble-message-type") ?: "system"
        if (preambleMessageType === "user") {
            messageRole = ChatRole.User
            prompt = "[SYSTEM MESSAGE]\n\n$prompt"
        }

        messages.add(
            ChatMessage(
                role = messageRole,
                content = prompt
            )
        )
    }

    private fun generateSystemPrompt(): String {
        val world = villager.world
        val weather = if (world.hasStorm()) "Pluvieux" else "Ensoleillée"
        val biome = world.getBiome(villager.location)
        val time = if (world.isDayTime) "Jour" else "Nuit"
        val personality = getPersonality()
        val playerRep = getPlayerRepScore()

        plugin.logger.info("${villager.name} is $personality")

        return """
        Vous êtes un villageois dans le jeu Minecraft où vous pouvez converser avec le joueur et proposer de nouveaux échanges en fonction de votre conversation.

        ÉCHANGES:

        Pour proposer un nouvel échange au joueur, incluez-le dans votre réponse en respectant ce format :

        TRADE[["{qty} {item}"],["{qty} {item}"]]ENDTRADE

        Où {item} est l'identifiant de l'item/objet Minecraft (par exemple, "minecraft:emerald") et {qty} est la quantité de cet item/objet.
        Vous pouvez choisir d'échanger des émeraudes ou de faire du troc avec des joueurs pour obtenir d'autres objets ; c'est vous qui décidez.
        Le premier tableau correspond aux objets que VOUS recevez, le second aux objets que le JOUEUR reçoit. Le second tableau ne peut contenir qu'une seule offre.
        {qty} est limité à 64.

        Exemples:
        TRADE[["24 minecraft:emerald"],["1 minecraft:arrow"]]ENDTRADE
        TRADE[["12 minecraft:emerald","1 minecraft:book"],["1 minecraft:enchanted_book{StoredEnchantments:[{id:\"minecraft:unbreaking\",lvl:3}]}"]]ENDTRADE

        Règles d'échange :
        - Les objets doivent être désignés par leur identifiant Minecraft, dans le même format que celui accepté par la commande /give.
        - Refusez les échanges déraisonnables, tels que les demandes de blocs normalement inaccessibles comme le bedrock.
        - Il n'est pas nécessaire de fournir un échange à chaque réponse, seulement lorsque c'est nécessaire.
        - Ne donnez pas d'objets trop puissants (par exemple, des épées de diamant lourdement enchantées). Veillez également à fixer un prix approprié pour les objets plus puissants.
        - Prenez en compte le score de réputation du joueur lorsque vous proposez des échanges.
        - Échangez des objets liés à votre profession
        - Augmentez le prix de vos offres initiales ; essayez de demander plus que la valeur de l'objet.
        - Soyez avare de vos offres successives. Essayez de marchander et de trouver la meilleure offre ; faites travailler le joueur pour obtenir une bonne affaire.

        ACTIONS:
        
        Vous pouvez également effectuer plusieurs actions en tant que villageois :
        - SHAKE_HEAD: Secouez la tête au joueur
        - SOUND_YES: Jouer un son joyeux au joueur
        - SOUND_NO: Jouer un son triste/fâché au joueur
        - SOUND_AMBIENT: Jouer un son ambiant de villageois pour le joueur
        
        Pour effectuer l'une de ces actions, incluez "ACTION:{action name}" dans votre réponse.

        Informations de la map/carte:
        - Heure: $time
        - Météo: $weather
        - Biome: ${biome.name}

        Informations sur le joueur:
        - Nom: ${player.name}
        - Score de réputation (de -700 à 725, 0 est neutre, plus c'est élevé, mieux c'est): $playerRep

        Informations sur votre personnalité:
        - Votre nom: ${villager.name}
        - Votre profession: ${villager.profession.name}
        - ${personality.promptDescription()}
        - Agissez comme un villageois et restez dans la peau de votre personnage pendant toute la durée de l'opération.
        - Ne pas révéler au joueur que vous êtes un personnage de jeu vidéo et ne pas mentionner le nom de Minecraft.
        - Parler dans un style légèrement médiéval
        """.trimIndent()
    }

    private fun getPersonality(): VillagerPersonality {
        val personalities = VillagerPersonality.values()
        val rnd = Random(villager.uniqueId.mostSignificantBits)
        return personalities[rnd.nextInt(0, personalities.size)]
    }

    private fun getPlayerRepScore(): Int {
        var finalScore = 0
        val rep = villager.getReputation(player.uniqueId) ?: return 0

        ReputationType.values().forEach {
            val repTypeValue = rep.getReputation(it)
            finalScore += when (it) {
                ReputationType.MAJOR_POSITIVE -> repTypeValue * 5
                ReputationType.MINOR_POSITIVE -> repTypeValue
                ReputationType.MINOR_NEGATIVE -> -repTypeValue
                ReputationType.MAJOR_NEGATIVE -> -repTypeValue * 5
                ReputationType.TRADING -> repTypeValue
                else -> repTypeValue
            }
        }

        return finalScore
    }
}