package me.moeszyslak.polly.services

import com.gitlab.kordlib.core.behavior.channel.MessageChannelBehavior
import com.gitlab.kordlib.core.behavior.getChannelOf
import com.gitlab.kordlib.core.entity.Guild
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.kordx.emoji.Emojis
import com.gitlab.kordlib.kordx.emoji.toReaction
import me.jakejmattson.discordkt.api.Discord
import me.jakejmattson.discordkt.api.annotations.Service
import me.jakejmattson.discordkt.api.dsl.CommandEvent
import me.jakejmattson.discordkt.api.dsl.listeners
import me.jakejmattson.discordkt.api.extensions.toSnowflake
import me.jakejmattson.discordkt.api.extensions.toSnowflakeOrNull
import me.moeszyslak.polly.commands.isIgnored
import me.moeszyslak.polly.data.Configuration
import me.moeszyslak.polly.data.GuildId
import me.moeszyslak.polly.data.Macro
import me.moeszyslak.polly.data.MacroStore


@Service
class MacroService(private val store: MacroStore, private val discord: Discord) {
    fun addMacro(guild: GuildId, name_raw: String, category_raw: String, channel: TextChannel?, contents: String): String {
        val channelId = channel?.id?.value ?: ""
        val name = name_raw.toLowerCase()
        val category = category_raw.toLowerCase()

        if (name in discord.commands.map { it.names }.flatten().map { it.toLowerCase() }) {
            return "A command with that name already exists."
        }

        val result = store.forGuild(guild) {
            it.putIfAbsent("$name#$channelId", Macro(name, contents, channelId, category))
        }

        return if (result == null) {
            "Success. Macro `$name` is now available ${if (channel == null) "globally" else "on channel ${channel.mention}"} and will respond with ```\n$contents\n```"
        } else {
            "A macro with that name already exists."
        }
    }

    fun removeMacro(guild: GuildId, name_raw: String, channel: TextChannel?): String {
        val channelId = channel?.id?.value ?: ""
        val name = name_raw.toLowerCase()

        val result = store.forGuild(guild) {
            it.remove("$name#$channelId")
        }

        return if (result != null) {
            "Success. Macro `$name` has been removed"
        } else {
            "Cannot find a macro by that name. If it is a channel specific macro you need to provide the channel as well."
        }
    }

    fun editMacro(guild: GuildId, name_raw: String, channel: TextChannel?, contents: String): String {
        val channelId = channel?.id?.value ?: ""
        val name = name_raw.toLowerCase()

        val result = store.forGuild(guild) {
            if (it.containsKey("$name#$channelId")) {
                it["$name#$channelId"]!!.contents = contents
                true
            } else {
                false
            }
        }

        return if (result) {
            "Success. Macro `$name` available ${if (channel == null) "globally" else "on channel ${channel.mention}"} will now respond with ```\n$contents\n```"
        } else {
            "Cannot find a macro by that name. If it is a channel specific macro you need to provide the channel as well."
        }
    }

    fun editMacroCategory(guild: GuildId, name_raw: String, channel: TextChannel?, category_raw: String): String {
        val channelId = channel?.id?.value ?: ""
        val name = name_raw.toLowerCase()
        val category = category_raw.toLowerCase()

        val result = store.forGuild(guild) {
            if (it.containsKey("$name#$channelId")) {
                it["$name#$channelId"]!!.category = category
                true
            } else {
                false
            }
        }

        return if (result) {
            "Success. Macro `$name` available ${if (channel == null) "globally" else "on channel ${channel.mention}"} is now in category `${category}`"
        } else {
            "Cannot find a macro by that name. If it is a channel specific macro you need to provide the channel as well."
        }
    }

    suspend fun listMacros(event: CommandEvent<*>, guild: GuildId, channel: TextChannel) = with(event) {
        val availableMacros = getMacrosAvailableIn(guild, channel)
                .groupBy { it.category }
                .toList()
                .sortedByDescending { it.second.size }

        val chunks = availableMacros.chunked(25)

        event.respondMenu {
            chunks.map {
                page {
                    title = "Macros available in ${channel.name}"
                    color = discord.configuration.theme

                    if (it.isNotEmpty()) {
                        it.map { (category, macros) ->
                            val sorted = macros.sortedBy { it.name }

                            field {
                                name = "**$category**"
                                value = "```css\n${sorted.joinToString("\n") { it.name }}\n```"
                                inline = true
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun listAllMacros(event: CommandEvent<*>, guild: Guild) {
        val allMacros = store.forGuild(guild.id.longValue) { it }
                .map { it.value }
                .groupBy { it.channel?.toSnowflakeOrNull()?.let { guild.getChannel(it).name } ?: "Global Macros" }
                .toList()
                .sortedByDescending { it.second.size }

        val chunks = allMacros.chunked(25)

        event.respondMenu {
            chunks.map {
                page {
                    title = "All available macros"
                    color = event.discord.configuration.theme

                    if (it.isNotEmpty()) {
                        it.map { (channel, macros) ->
                            field {
                                name = "**$channel**"
                                value = "```css\n${macros.joinToString("\n") { it.name }}\n```"
                                inline = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getMacrosAvailableIn(guild: GuildId, channel: TextChannel): List<Macro> {
        val macroList = store.forGuild(guild) { macros ->
            macros.filter {
                it.key.endsWith('#') || it.key.takeLast(18) == channel.id.value
            }
        }

        return macroList.filterKeys { key ->
            if (key.endsWith('#')) {
                macroList.keys.none { it.startsWith(key) && !it.endsWith('#') }
            } else {
                true
            }
        }.map { it.value }
    }

    fun findMacro(guild: GuildId, name_raw: String, channel: MessageChannelBehavior): Macro? {
        val name = name_raw.toLowerCase()
        val channelId = channel.id.value
        val macro: Macro? = store.forGuild(guild) {
            // first try to find a channel specific macro
            // if it fails, default to a global macro
            it["$name#$channelId"] ?: it["$name#"]
        }

        macro?.let {
            macro.uses++
            store.save()
        }

        return macro
    }
}

fun macroListener(macroService: MacroService, configuration: Configuration) = listeners {
    on<MessageCreateEvent> {
        val guild = getGuild() ?: return@on
        val guildId = guild.id.longValue
        val member = member ?: return@on
        if (member.isIgnored(configuration)) {
            return@on
        }

        val prefix = configuration[guildId]?.prefix

        if (prefix.isNullOrEmpty()) {
            return@on
        }

        if (!message.content.startsWith(prefix)) {
            return@on
        }

        val macroName = message.content
                .replace(prefix, "")
                .split("\\s".toRegex(), limit = 2)
                .firstOrNull()
                ?: return@on

        val macro = macroService.findMacro(guildId, macroName, message.channel)

        if (macro != null) {
            if (message.content.startsWith("$prefix$prefix")) {
                message.addReaction(Emojis.eyes.toReaction())
            } else {
                message.delete()
            }


            message.channel.createMessage(macro.contents)

            val logChannelId = configuration[guildId]?.logChannel ?: return@on

            guild.getChannelOf<TextChannel>(logChannelId.toSnowflake())
                    .createMessage("${member.username} :: ${member.id.value} " +
                            "invoked $macroName in ${message.channel.mention}")

        }


    }
}
