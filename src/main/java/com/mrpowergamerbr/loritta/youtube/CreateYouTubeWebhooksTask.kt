package com.mrpowergamerbr.loritta.youtube

import com.github.kevinsawicki.http.HttpRequest
import com.github.salomonbrys.kotson.fromJson
import com.google.common.flogger.FluentLogger
import com.mongodb.client.model.Filters
import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.userdata.ServerConfig
import com.mrpowergamerbr.loritta.utils.gson
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.lorittaShards
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CreateYouTubeWebhooksTask : Runnable {
	companion object {
		private val logger = FluentLogger.forEnclosingClass()
		val lastNotified = ConcurrentHashMap<String, Long>()
	}

	var youtubeWebhooks: MutableList<YouTubeWebhook>? = null

	override fun run() {
		try {
			// Servidores que usam o módulo do YouTube
			val servers = loritta.serversColl.find(
					Filters.gt("youTubeConfig.channels", listOf<Any>())
			)

			// IDs dos canais a serem verificados
			val channelIds = mutableSetOf<String>()

			val list = mutableListOf<ServerConfig>()

			logger.atInfo().log("Verificando canais do YouTube de %s servidores...", servers.count())

			servers.iterator().use {
				while (it.hasNext()) {
					val server = it.next()
					val guild = lorittaShards.getGuildById(server.guildId) ?: continue
					val youTubeConfig = server.youTubeConfig

					for (channel in youTubeConfig.channels) {
						if (channel.channelId == null)
							continue
						if (!channel.channelUrl!!.startsWith("http"))
							continue
						val textChannel = guild.getTextChannelById(channel.repostToChannelId) ?: continue

						if (!textChannel.canTalk())
							continue

						channelIds.add(channel.channelId!!)
					}
					list.add(server)
				}
			}

			channelIds.forEach {
				// Caso o channel ID não esteja na map de lastNotified, vamos salvar o tempo atual nela (para evitar que anuncie coisas "do passado")
				if (!lastNotified.containsKey(it))
					lastNotified[it] = System.currentTimeMillis()
			}

			val youtubeWebhookFile = File(Loritta.FOLDER, "youtube_webhook.json")
			if (youtubeWebhooks == null && youtubeWebhookFile.exists()) {
				youtubeWebhooks = gson.fromJson(youtubeWebhookFile.readText())
			} else if (youtubeWebhooks == null) {
				youtubeWebhooks = mutableListOf()
			}

			val notCreatedYetChannels = mutableListOf<String>()

			logger.atInfo().log("Existem %s canais no YouTUbe que eu irei verificar! Atualmente existem %s webhooks criadas!", channelIds.size, youtubeWebhooks!!.size)

			for (channelId in channelIds) {
				val webhook = youtubeWebhooks!!.firstOrNull { it.channelId == channelId }

				if (webhook == null) {
					notCreatedYetChannels.add(channelId)
					continue
				}

				if (System.currentTimeMillis() > webhook.createdAt + (webhook.lease * 1000)) {
					logger.atFine().log("Webhook de %s expirou! Nós iremos recriar ela...", channelId)
					youtubeWebhooks!!.remove(webhook)
					notCreatedYetChannels.add(channelId)
				}
			}

			logger.atInfo().log("Irei criar %s webhooks para canais no YouTube!", notCreatedYetChannels.size)

			val webhooksToBeCreatedCount = notCreatedYetChannels.size

			val webhookCount = AtomicInteger()

			val tasks = notCreatedYetChannels.map {channelId ->
				async(loritta.coroutineDispatcher, start = CoroutineStart.LAZY) {
					try {
						// Iremos primeiro desregistrar todos os nossos testes marotos
						HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub",
										"hub.lease_seconds" to "",
										"hub.mode" to "unsubscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.ok()


						HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub?type=youtube",
										"hub.lease_seconds" to "",
										"hub.mode" to "unsubscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.ok()

						HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub?type=youtubevideo",
										"hub.lease_seconds" to "",
										"hub.mode" to "unsubscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.ok()

						HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub?type=youtubevideoupdate",
										"hub.lease_seconds" to "",
										"hub.mode" to "unsubscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.ok()

						HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub?type=ytvideo",
										"hub.lease_seconds" to "",
										"hub.mode" to "unsubscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.ok()

						// E agora realmente iremos criar!
						val code = HttpRequest.post("https://pubsubhubbub.appspot.com/subscribe")
								.form(mapOf(
										"hub.callback" to "https://loritta.website/api/v1/callbacks/pubsubhubbub?type=ytvideo",
										"hub.lease_seconds" to "",
										"hub.mode" to "subscribe",
										"hub.secret" to Loritta.config.mixerWebhookSecret,
										"hub.topic" to "https://www.youtube.com/xml/feeds/videos.xml?channel_id=$channelId",
										"hub.verify" to "async",
										"hub.verify_token" to Loritta.config.mixerWebhookSecret
								))
								.code()

						if (code != 204 && code != 202) { // code 204 = noop, 202 = accepted (porque pelo visto o PubSubHubbub usa os dois
							logger.atSevere().log("Erro ao tentar criar Webhook de %s! Código: %s", channelId, code)
							return@async null
						}

						logger.atFine().log("Webhook de %s criada com sucesso! Atualmente %s/%s webhooks foram criadas!", channelId, webhookCount.incrementAndGet(), webhooksToBeCreatedCount)
						return@async YouTubeWebhook(
								channelId,
								System.currentTimeMillis(),
								432000
						)
					} catch (e: Exception) {
						logger.atSevere().withCause(e).log("Erro ao criar subscription no YouTube")
						null
					}
				}
			}

			runBlocking {
				tasks.onEach {
					val webhook = it.await()

					if (webhook != null) {
						youtubeWebhooks!!.add(webhook)
					}
				}

				youtubeWebhookFile.writeText(gson.toJson(youtubeWebhooks))
			}
		} catch (e: Exception) {
			logger.atSevere().withCause(e).log("Erro ao processar vídeos do YouTube")
		}
	}
}