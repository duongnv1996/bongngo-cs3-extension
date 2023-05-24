package com.loklok

import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import okio.ByteString.Companion.encode
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object LoklokUtils : Loklok() {

    private fun cryptoHandler(
        string: String,
        secretKeyString: String,
        encrypt: Boolean = true
    ): String {
        val secretKey = SecretKeySpec(secretKeyString.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING")
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            String(cipher.doFinal(base64DecodeArray(string)))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            base64Encode(cipher.doFinal(string.toByteArray()))
        }
    }

    fun getAesKey(deviceId: String): String? {
        val publicKey = RSAEncryptionHelper.getPublicKeyFromString(RSA_PUBLIC_KEY) ?: return null
        return RSAEncryptionHelper.encryptText(deviceId, publicKey)
    }

    fun getSign(currentTime: String, params:  Map<String, String>, deviceId: String): String? {
        val chipper = listOf(currentTime, params.map { it.value }.joinToString("")).joinToString("")
        val enc = cryptoHandler(chipper, deviceId)
        return md5(enc)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    fun upgradeSoraUrl(url: String) : String {
        val expiry = System.currentTimeMillis() + 60 * 60 * 12 * 7
        val mac = "fuckfuck".encode().hmacSha256("$expiry".encode()).hex()
        return "${url.replace(preview, akm).substringBefore(".m3u8")}.m3u8"
    }

    fun getDeviceId(length: Int = 16): String {
        val allowedChars = ('a'..'f') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun getQuality(quality: String): Int {
        return when (quality) {
            "GROOT_FD" -> Qualities.P360.value
            "GROOT_LD" -> Qualities.P480.value
            "GROOT_SD" -> Qualities.P720.value
            "GROOT_HD" -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }

    fun getLanguage(str: String): String {
        return when (str) {
            "in_ID" -> "Indonesian"
            "pt" -> "Portuguese"
            else -> str.split("_").first().let {
                SubtitleHelper.fromTwoLettersToLanguage(it).toString()
            }
        }
    }

}