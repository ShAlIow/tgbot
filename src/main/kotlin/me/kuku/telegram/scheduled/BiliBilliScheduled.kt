package me.kuku.telegram.scheduled

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendPhoto
import com.pengrad.telegrambot.request.SendVideo
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import me.kuku.telegram.config.TelegramConfig
import me.kuku.telegram.entity.*
import me.kuku.telegram.logic.BiliBiliLogic
import me.kuku.telegram.logic.BiliBiliPojo
import me.kuku.telegram.utils.sendPic
import me.kuku.utils.client
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
class BiliBilliScheduled(
    private val biliBiliService: BiliBiliService,
    private val telegramBot: TelegramBot,
    private val telegramConfig: TelegramConfig,
    private val logService: LogService
) {

    private val liveMap = mutableMapOf<Long, MutableMap<Long, Boolean>>()
    private val userMap = mutableMapOf<Long, Long>()


    @Scheduled(cron = "0 23 3 * * ?")
    suspend fun sign() {
        val list = biliBiliService.findBySign(Status.ON)
        for (biliBiliEntity in list) {
            val logEntity = LogEntity().apply {
                tgId = biliBiliEntity.tgId
                type = LogType.BiliBili
            }
            kotlin.runCatching {
                val firstRank = BiliBiliLogic.ranking()[0]
                delay(5000)
                BiliBiliLogic.report(biliBiliEntity, firstRank.aid, firstRank.cid, 300)
                delay(5000)
                BiliBiliLogic.share(biliBiliEntity, firstRank.aid)
                delay(5000)
                BiliBiliLogic.liveSign(biliBiliEntity)
                logEntity.text = "成功"
            }.onFailure {
                logEntity.text = "失败"
                logEntity.errReason = it.message ?: "未知异常原因"
                logEntity.sendFailMessage(it.message)
            }
            logService.save(logEntity)
            delay(3000)
        }
    }

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    suspend fun liveMonitor() {
        val list = biliBiliService.findByLive(Status.ON)
        for (biliBiliEntity in list) {
            val result = BiliBiliLogic.followed(biliBiliEntity)
            delay(3000)
            if (result.failure()) continue
            val tgId = biliBiliEntity.tgId
            if (!liveMap.containsKey(tgId)) liveMap[tgId] = mutableMapOf()
            val map = liveMap[tgId]!!
            for (up in result.data()) {
                val id = up.id.toLong()
                val name = up.name
                delay(3000)
                val live = BiliBiliLogic.live(id.toString())
                if (live.id.isEmpty()) continue
                val b = live.status
                if (map.containsKey(id)) {
                    if (map[id] != b) {
                        map[id] = b
                        val msg = if (b) "直播啦！！" else "下播了！！"
                        val text = "#哔哩哔哩开播提醒\n#$name $msg\n标题：${live.title}\n链接：${live.url}"
                        val imageUrl = live.imageUrl
                        if (imageUrl.isEmpty())
                            telegramBot.execute(SendMessage(tgId, text))
                        else {
                            client.get(imageUrl).body<ByteArray>().let {
                                val sendPhoto = SendPhoto(tgId, it).caption(text).fileName("live.jpg")
                                telegramBot.execute(sendPhoto)
                            }
                        }
                    }
                } else map[id] = b
            }
        }
    }

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    suspend fun userMonitor() {
        val biliBiliList = biliBiliService.findByPush(Status.ON)
        for (biliBiliEntity in biliBiliList) {
            val tgId = biliBiliEntity.tgId
            delay(3000)
            val result = BiliBiliLogic.friendDynamic(biliBiliEntity)
            val list = result.data ?: continue
            val newList = mutableListOf<BiliBiliPojo>()
            if (userMap.containsKey(tgId)) {
                val oldId = userMap[tgId]!!
                for (biliBiliPojo in list) {
                    if (biliBiliPojo.id.toLong() <= oldId) break
                    newList.add(biliBiliPojo)
                }
                for (biliBiliPojo in newList) {
                    val text = "#哔哩哔哩动态推送\n${BiliBiliLogic.convertStr(biliBiliPojo)}"
                    val bvId = if (biliBiliPojo.bvId.isNotEmpty()) biliBiliPojo.bvId
                    else if (biliBiliPojo.forwardBvId.isNotEmpty()) biliBiliPojo.forwardBvId
                    else ""
                    try {
                        if (bvId.isNotEmpty() && telegramConfig.url.isNotEmpty()) {
                            var file: File? = null
                            try {
                                delay(3000)
                                file = BiliBiliLogic.videoByBvId(biliBiliEntity, biliBiliPojo.bvId)
                                val sendVideo =
                                    SendVideo(tgId, file).caption(text)
                                telegramBot.execute(sendVideo)
                            } finally {
                                file?.delete()
                            }
                        } else if (biliBiliPojo.picList.isNotEmpty() || biliBiliPojo.forwardPicList.isNotEmpty()) {
                            val picList = biliBiliPojo.picList
                            picList.addAll(biliBiliPojo.forwardPicList)
                            telegramBot.sendPic(tgId, text, picList)
                        } else telegramBot.execute(SendMessage(tgId, text))
                    } catch (e: Exception) {
                        telegramBot.execute(SendMessage(tgId, text))
                    }
                }
            }
            userMap[tgId] = list[0].id.toLong()
        }
    }

}
