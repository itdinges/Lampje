package app.lampje.core

import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** What we need to talk to a paired bridge. [certSha256] pins the bridge's leaf certificate. */
data class BridgeConfig(val ip: String, val appKey: String, val certSha256: String)

/**
 * The Hue bridge uses a certificate signed by a private Signify CA, so system trust fails.
 * We use trust-on-first-use: the first fingerprint seen (while pairing) is stored and every
 * later connection must present the same leaf certificate.
 */
class PinningTrustManager(private val expectedSha256: String?) : X509TrustManager {
    @Volatile
    var seenSha256: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
        val fingerprint = sha256Hex(leaf.encoded)
        seenSha256 = fingerprint
        if (expectedSha256 != null && !expectedSha256.equals(fingerprint, ignoreCase = true)) {
            throw CertificateException("Bridge certificate changed (expected $expectedSha256)")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

object HueConnect {
    fun baseUrl(ip: String): HttpUrl = "https://$ip".toHttpUrl()

    fun httpClient(host: String, trust: PinningTrustManager): OkHttpClient {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { hostname, _ -> hostname == host }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun api(config: BridgeConfig): HueApi =
        HueApi(baseUrl(config.ip), config.appKey, httpClient(config.ip, PinningTrustManager(config.certSha256)))

    /** Press the bridge's link button first; throws [LinkButtonNotPressedException] otherwise. */
    suspend fun pair(ip: String, deviceType: String = "lampje#android"): BridgeConfig {
        val trust = PinningTrustManager(null)
        val appKey = HueApi.pair(baseUrl(ip), httpClient(ip, trust), deviceType)
        val fingerprint = trust.seenSha256 ?: throw HueApiException(0, "No certificate seen while pairing")
        return BridgeConfig(ip, appKey, fingerprint)
    }
}
