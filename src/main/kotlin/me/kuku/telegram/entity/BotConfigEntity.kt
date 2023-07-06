@file:Suppress("SpringDataRepositoryMethodReturnTypeInspection")

package me.kuku.telegram.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Service

@Document("bot_config")
class BotConfigEntity {
    @Id
    var id: String? = null
    @Indexed(unique = true)
    var token: String = ""
    var blacklist: MutableList<Long> = mutableListOf()
    var admins: MutableList<Long> = mutableListOf()
    var pushUrl: String = ""
    // 公用
    var rrOcrKey: String = ""
    var twoCaptchaKey: String = ""
}


interface BotConfigRepository: CoroutineCrudRepository<BotConfigEntity, String> {

    suspend fun findByToken(token: String): BotConfigEntity?

}

@Service
class BotConfigService(
    private val botConfigRepository: BotConfigRepository
) {

    suspend fun findByToken(token: String) = botConfigRepository.findByToken(token)

    suspend fun save(entity: BotConfigEntity) = botConfigRepository.save(entity)

}
