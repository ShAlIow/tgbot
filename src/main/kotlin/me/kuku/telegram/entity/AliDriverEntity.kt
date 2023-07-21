package me.kuku.telegram.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Service

@Document("ali_driver")
class AliDriverEntity: BaseEntity() {
    @Id
    var id: String? = null
    var refreshToken: String = ""
    var sign: Status = Status.OFF
    var receive: Status = Status.OFF
    var joinTeam: Status = Status.OFF
}

@Suppress("SpringDataRepositoryMethodReturnTypeInspection")
interface AliDriverRepository: CoroutineCrudRepository<AliDriverEntity, String> {

    suspend fun findByTgId(tgId: Long): AliDriverEntity?

    suspend fun findBySign(sign: Status): List<AliDriverEntity>

    suspend fun deleteByTgId(tgId: Long)

    suspend fun findByJoinTeam(status: Status): List<AliDriverEntity>
}

@Service
class AliDriverService(
    private val aliDriverRepository: AliDriverRepository
) {

    suspend fun findByTgId(tgId: Long) = aliDriverRepository.findByTgId(tgId)

    suspend fun findBySign(sign: Status) = aliDriverRepository.findBySign(sign)

    suspend fun save(aliDriverEntity: AliDriverEntity): AliDriverEntity = aliDriverRepository.save(aliDriverEntity)

    suspend fun delete(aliDriverEntity: AliDriverEntity) = aliDriverRepository.delete(aliDriverEntity)

    suspend fun deleteByTgId(tgId: Long) = aliDriverRepository.deleteByTgId(tgId)

    suspend fun findByJoinTeam(status: Status) = aliDriverRepository.findByJoinTeam(status)
}
