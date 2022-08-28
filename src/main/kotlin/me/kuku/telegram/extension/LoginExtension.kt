package me.kuku.telegram.extension

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import me.kuku.telegram.entity.*
import me.kuku.telegram.logic.*
import me.kuku.telegram.utils.ability
import me.kuku.telegram.utils.callback
import me.kuku.telegram.utils.execute
import me.kuku.telegram.utils.waitNextMessage
import me.kuku.utils.OkHttpKtUtils
import me.kuku.utils.base64Decode
import me.kuku.utils.toUrlEncode
import org.springframework.stereotype.Service
import org.telegram.abilitybots.api.util.AbilityExtension
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Service
class LoginExtension(
    private val biliBiliService: BiliBiliService,
    private val baiduLogic: BaiduLogic,
    private val baiduService: BaiduService,
    private val douYuLogic: DouYuLogic,
    private val douYuService: DouYuService,
    private val hostLocService: HostLocService,
    private val huYaLogic: HuYaLogic,
    private val huYaService: HuYaService,
    private val kuGouService: KuGouService,
    private val kuGouLogic: KuGouLogic,
    private val netEaseService: NetEaseService,
    private val stepService: StepService,
    private val weiboService: WeiboService,
    private val miHoYoService: MiHoYoService
): AbilityExtension {

    private fun loginKeyboardMarkup(): InlineKeyboardMarkup {
        val baiduButton = InlineKeyboardButton("百度").also { it.callbackData = "baiduLogin" }
        val biliBiliButton = InlineKeyboardButton("哔哩哔哩").also { it.callbackData = "biliBiliLogin" }
        val douYuButton = InlineKeyboardButton("斗鱼").also { it.callbackData = "douYuLogin" }
        val hostLocButton = InlineKeyboardButton("HostLoc").also { it.callbackData = "hostLocLogin" }
        val huYaButton = InlineKeyboardButton("虎牙").also { it.callbackData = "huYaLogin" }
        val kuGouButton = InlineKeyboardButton("酷狗").also { it.callbackData = "kuGouLogin" }
        val miHoYoButton = InlineKeyboardButton("米忽悠").also { it.callbackData = "miHoYoLogin" }
        val netEaseButton = InlineKeyboardButton("网易云音乐").also { it.callbackData = "netEaseLogin" }
        val xiaomiStepButton = InlineKeyboardButton("小米运动").also { it.callbackData = "xiaomiStepLogin" }
        val leXinStepButton = InlineKeyboardButton("乐心运动").also { it.callbackData = "leXinStepLogin" }
        val weiboStepButton = InlineKeyboardButton("微博").also { it.callbackData = "weiboLogin" }
        return InlineKeyboardMarkup(listOf(
            listOf(baiduButton, biliBiliButton),
            listOf(douYuButton, hostLocButton),
            listOf(huYaButton, kuGouButton),
            listOf(miHoYoButton, netEaseButton),
            listOf(xiaomiStepButton, leXinStepButton),
            listOf(weiboStepButton)
        ))
    }

    fun login() = ability("login", "登录") {
        val markup = loginKeyboardMarkup()
        val sendMessage = SendMessage()
        sendMessage.replyMarkup = markup
        sendMessage.chatId = chatId().toString()
        sendMessage.text = "请选择登录选项"
        execute(sendMessage)
    }

    fun baiduLogin() = callback("baiduLogin") {
        val qrcode = baiduLogic.getQrcode()
        val bytes = OkHttpKtUtils.getBytes(qrcode.image)
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(bytes.inputStream(), "百度登录二维码.jpg"))
            .apply { caption = "请使用百度app扫码登陆，百度网盘等均可" }
        execute(photo)
        withTimeout(1000 * 60 * 2) {
            val baiduEntity = baiduService.findByTgId(it.from.id) ?: BaiduEntity().apply {
                tgId = it.from.id
            }
            while (true) {
                try {
                    val result = baiduLogic.checkQrcode(qrcode)
                    if (result.success()) {
                        val newEntity = result.data()
                        baiduEntity.cookie = newEntity.cookie
                        baiduService.save(baiduEntity)
                        val sendMessage = SendMessage(it.message.chatId.toString(), "绑定百度成功")
                        execute(sendMessage)
                    }
                } catch (ignore: Exception) {}
            }
        }
    }

    fun biliBiliLogin() = callback("biliBiliLogin") {
        val qrCodeUrl = BiliBiliLogic.loginByQr1()
        val bytes = OkHttpKtUtils.getBytes("https://api.kukuqaq.com/qrcode?text=${qrCodeUrl.toUrlEncode()}")
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(bytes.inputStream(), "哔哩哔哩登录二维码.jpg"))
                .apply { caption = "请使用哔哩哔哩app扫码登陆" }
        val photoMessage = execute(photo)
        while (true) {
            delay(3000)
            val result = BiliBiliLogic.loginByQr2(qrCodeUrl)
            when (result.code) {
                0 -> continue
                200 -> {
                    val newEntity = result.data()
                    val biliBiliEntity = biliBiliService.findByTgId(it.from.id) ?: BiliBiliEntity().also { entity ->
                        entity.tgId = it.from.id
                    }
                    biliBiliEntity.cookie = newEntity.cookie
                    biliBiliEntity.userid = newEntity.userid
                    biliBiliEntity.token = newEntity.token
                    biliBiliService.save(biliBiliEntity)
                    val message = SendMessage().also { age ->
                        age.chatId = it.message.chatId.toString()
                        age.text = "绑定哔哩哔哩成功"
                    }
                    val titleMessage = execute(message)
                    delay(1000 * 10)
                    execute(DeleteMessage(it.message.chatId.toString(), photoMessage.messageId))
                    execute(DeleteMessage(it.message.chatId.toString(), titleMessage.messageId))
                    break
                }
                else -> {
                    val message = SendMessage().also { age ->
                        age.chatId = it.message.chatId.toString()
                        age.text = result.message
                    }
                    val titleMessage = execute(message)
                    delay(1000 * 10)
                    execute(DeleteMessage(it.message.chatId.toString(), photoMessage.messageId))
                    execute(DeleteMessage(it.message.chatId.toString(), titleMessage.messageId))
                    break
                }
            }
        }
    }

    fun douYuLogin() = callback("douYuLogin") {
        val qrcode = douYuLogic.getQrcode()
        val imageBase = qrcode.qqLoginQrcode.imageBase
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(imageBase.base64Decode().inputStream(),
            "斗鱼登录二维码.jpg")).apply { caption = "请使用斗鱼绑定qq，然后使用qq扫码登录" }
        execute(photo)
        while (true) {
            delay(3000)
            val result = douYuLogic.checkQrcode(qrcode)
            when (result.code) {
                0 -> continue
                200 -> {
                    val newEntity = result.data()
                    val douYuEntity = douYuService.findByTgId(it.from.id) ?: DouYuEntity().apply {
                        tgId = it.from.id
                    }
                    douYuEntity.cookie = newEntity.cookie
                    douYuService.save(douYuEntity)
                    val sendMessage = SendMessage(it.message.chatId.toString(), "绑定斗鱼成功")
                    execute(sendMessage)
                    break
                }
                else -> {
                    val sendMessage = SendMessage(it.message.chatId.toString(), result.message)
                    execute(sendMessage)
                    break
                }
            }
        }
    }

    fun hostLocLogin() = callback("hostLocLogin") {
        val chatId = it.message.chatId
        val accountSendMessage = SendMessage(chatId.toString(), "请发送账号")
        execute(accountSendMessage)
        val account = it.waitNextMessage().text
        val passwordSendMessage = SendMessage(chatId.toString(), "请发送密码")
        execute(passwordSendMessage)
        val password = it.waitNextMessage().text
        val res = HostLocLogic.login(account, password)
        val text = if (res.success()) {
            val cookie = res.data()
            val hostLocEntity = hostLocService.findByTgId(chatId) ?: HostLocEntity().apply { tgId = chatId }
            hostLocEntity.cookie = cookie
            hostLocService.save(hostLocEntity)
            "绑定HostLoc成功"
        } else res.message
        val sendMessage = SendMessage(chatId.toString(), text)
        execute(sendMessage)
    }

    fun huYaLogin() = callback("huYaLogin") {
        val chatId = it.message.chatId
        val qrcode = huYaLogic.getQrcode()
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(OkHttpKtUtils.getBytes(qrcode.url).inputStream(),
            "虎牙登录二维码.jpg")).apply { caption = "请使用虎牙App扫码登录" }
        execute(photo)
        while (true) {
            delay(3000)
            val result = huYaLogic.checkQrcode(qrcode)
            when (result.code) {
                0 -> continue
                200 -> {
                    val newEntity = result.data()
                    val huYaEntity = huYaService.findByTgId(chatId) ?: HuYaEntity().also { entity ->
                        entity.tgId = chatId
                    }
                    huYaEntity.cookie = newEntity.cookie
                    huYaService.save(huYaEntity)
                    val sendMessage = SendMessage(chatId.toString(), "绑定虎牙成功")
                    execute(sendMessage)
                    break
                }
                else -> {
                    val sendMessage = SendMessage(chatId.toString(), result.message)
                    execute(sendMessage)
                    break
                }
            }
        }
    }

    fun kuGouLogin() = callback("kuGouLogin") {
        val chatId = it.message.chatId
        execute(SendMessage(chatId.toString(), "请发送手机号"))
        val phone = it.waitNextMessage().text.toLongOrNull() ?: return@callback kotlin.run {
            execute(SendMessage(chatId.toString(), "发送的手机号有误"))
        }
        val kuGouEntity = kuGouService.findByTgId(chatId) ?: KuGouEntity().apply {
            mid = kuGouLogic.mid()
            tgId = chatId
        }
        val mid = kuGouEntity.mid
        val result = kuGouLogic.sendMobileCode(phone.toString(), mid)
        val message = if (result.success()) {
            execute(SendMessage(chatId.toString(), "请发送短信验证码"))
            val code = it.waitNextMessage(1000 * 60 * 2).text
            val verifyResult = kuGouLogic.verifyCode(phone.toString(), code, mid)
            if (verifyResult.success()) {
                val newKuGouEntity = verifyResult.data()
                kuGouEntity.kuGoo = newKuGouEntity.kuGoo
                kuGouEntity.token = newKuGouEntity.token
                kuGouEntity.userid = newKuGouEntity.userid
                kuGouService.save(kuGouEntity)
                "绑定成功"
            } else verifyResult.message
        } else result.message
        execute(SendMessage(chatId.toString(), message))
    }

    fun miHoYoLogin() = callback("miHoYoLogin") {
        val chatId = it.message.chatId
        execute(SendMessage(chatId.toString(), "请发送米哈游的cookie"))
        val cookie = it.waitNextMessage().text
        val newEntity = miHoYoService.findByTgId(chatId) ?: MiHoYoEntity().apply {
            tgId = chatId
        }
        newEntity.cookie = cookie
        miHoYoService.save(newEntity)
        execute(SendMessage(chatId.toString(), "绑定米哈游成功"))
    }

    fun netEaseLogin() = callback("netEaseLogin") {
        val chatId = it.message.chatId
        val key = NetEaseLogic.qrcode()
        val url = "http://music.163.com/login?codekey=$key"
        val newUrl =
            "https://api.kukuqaq.com/qrcode?text=${url.toUrlEncode()}"
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(OkHttpKtUtils.getBytes(newUrl).inputStream(),
            "网易云音乐登录二维码.jpg")).apply { caption = "请使用网易云音乐App扫码登录" }
        execute(photo)
        var scan = true
        while (true) {
            delay(3000)
            val result = NetEaseLogic.checkQrcode(key)
            when (result.code) {
                200 -> {
                    val netEaseEntity = result.data()
                    val newEntity = netEaseService.findByTgId(chatId) ?: NetEaseEntity().apply {
                        tgId = chatId
                    }
                    newEntity.csrf = netEaseEntity.csrf
                    newEntity.musicU = netEaseEntity.musicU
                    netEaseService.save(newEntity)
                    execute(SendMessage(chatId.toString(), "绑定网易云音乐成功"))
                    break
                }
                500 -> {
                    execute(SendMessage(chatId.toString(), result.message))
                    break
                }
                1 -> {
                    if (scan) {
                        execute(SendMessage(chatId.toString(), result.message))
                        scan = false
                    }
                }
            }
        }
    }

    fun xiaomiStepLogin() = callback("xiaomiStepLogin") {
        val chatId = it.message.chatId
        execute(SendMessage(chatId.toString(), "请发送手机号"))
        val phone = it.waitNextMessage().text
        execute(SendMessage(chatId.toString(), "请发送密码"))
        val password = it.waitNextMessage().text
        val result = XiaomiStepLogic.login(phone, password)
        val message = if (result.success()) {
            val newEntity = result.data()
            val stepEntity = stepService.findByTgId(chatId) ?: StepEntity().apply {
                tgId = chatId
            }
            stepEntity.miLoginToken = newEntity.miLoginToken
            stepService.save(stepEntity)
            "绑定小米运动成功"
        } else result.message
        execute(SendMessage(chatId.toString(), message))
    }

    fun leXinStepLogin() = callback("leXinStepLogin") {
        val chatId = it.message.chatId
        execute(SendMessage(chatId.toString(), "请发送手机号"))
        val phone = it.waitNextMessage().text
        execute(SendMessage(chatId.toString(), "请发送密码"))
        val password = it.waitNextMessage().text
        val result = LeXinStepLogic.login(phone, password)
        val message = if (result.success()) {
            val newStepEntity = result.data()
            val stepEntity = stepService.findByTgId(chatId) ?: StepEntity().apply {
                tgId = chatId
            }
            stepEntity.leXinCookie = newStepEntity.leXinCookie
            stepEntity.leXinUserid = newStepEntity.leXinUserid
            stepEntity.leXinAccessToken = newStepEntity.leXinAccessToken
            stepService.save(stepEntity)
            "绑定乐心运动成功"
        } else result.message
        execute(SendMessage(chatId.toString(), message))
    }

    fun weiboLogin() = callback("weiboLogin") {
        val chatId = it.message.chatId
        val weiboQrcode = WeiboLogic.loginByQr1()
        val url = weiboQrcode.url
        val bytes = OkHttpKtUtils.getBytes("https:$url")
        val photo = SendPhoto(it.message.chatId.toString(), InputFile(bytes.inputStream(),
            "微博登录二维码.jpg")).apply { caption = "请使用微博APP扫码登录" }
        execute(photo)
        while (true) {
            delay(3000)
            val result = WeiboLogic.loginByQr2(weiboQrcode)
            if (result.success()) {
                val newWeiboEntity = result.data()
                val weiboEntity = weiboService.findByTgId(chatId) ?: WeiboEntity().apply {
                    tgId = chatId
                }
                weiboEntity.pcCookie = newWeiboEntity.pcCookie
                weiboEntity.mobileCookie = newWeiboEntity.mobileCookie
                weiboService.save(weiboEntity)
                execute(SendMessage.builder().text("绑定微博成功").chatId(chatId).build())
                break
            } else if (result.code in listOf(201, 202)) continue
            else {
                execute(SendMessage.builder().text(result.message).chatId(chatId).build())
                break
            }
        }
    }

}