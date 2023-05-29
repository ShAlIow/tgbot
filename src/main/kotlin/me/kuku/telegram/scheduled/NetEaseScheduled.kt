package me.kuku.telegram.scheduled

import kotlinx.coroutines.delay
import me.kuku.telegram.entity.*
import me.kuku.telegram.logic.NetEaseLogic
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NetEaseScheduled(
    private val netEaseService: NetEaseService,
    private val logService: LogService
) {

    @Scheduled(cron = "0 12 7 * * ?")
    suspend fun sign() {
        val list = netEaseService.findBySign(Status.ON)
        for (netEaseEntity in list) {
            val logEntity = LogEntity().also {
                it.type = LogType.NetEase
                it.tgId = netEaseEntity.tgId
            }
            kotlin.runCatching {
                delay(3000)
                NetEaseLogic.listenMusic(netEaseEntity)
                delay(3000)
                val result = NetEaseLogic.sign(netEaseEntity)
                if (result.failure()) {
                    logEntity.text = "失败"
                    logEntity.errReason = result.message
                    logEntity.sendFailMessage(result.message)
                } else {
                    logEntity.text = "成功"
                }
            }.onFailure {
                logEntity.text = "失败"
                logEntity.errReason = it.message ?: "未知异常原因"
                logEntity.sendFailMessage(it.message)
            }
            logService.save(logEntity)
        }
    }

    @Scheduled(cron = "0 32 8 * * ?")
    suspend fun musicianSign() {
        val list = netEaseService.findByMusicianSign(Status.ON)
        for (netEaseEntity in list) {
            val logEntity = LogEntity().also {
                it.type = LogType.NetEaseMusician
                it.tgId = netEaseEntity.tgId
            }
            kotlin.runCatching {
                var b = false
                var errorReason: String? = null
                for (i in 0..1) {
                    val result = NetEaseLogic.musicianSign(netEaseEntity)
                    if (result.success()) {
                        b = true
                        delay(3000)
                        NetEaseLogic.publish(netEaseEntity)
                        delay(3000)
                        NetEaseLogic.publishMLog(netEaseEntity)
                        delay(3000)
                        NetEaseLogic.myMusicComment(netEaseEntity)
                        delay(1000 * 60)
                    } else {
                        errorReason = result.message
                    }
                }
                if (b) {
                    logEntity.text = "成功"
                } else {
                    logEntity.text = "失败"
                    logEntity.errReason = errorReason ?: ""
                    logEntity.sendFailMessage(errorReason)
                }
            }.onFailure {
                logEntity.text = "失败"
                logEntity.errReason = it.message ?: "未知异常原因"
                logEntity.sendFailMessage(it.message)
            }
            logService.save(logEntity)
        }
    }

}
