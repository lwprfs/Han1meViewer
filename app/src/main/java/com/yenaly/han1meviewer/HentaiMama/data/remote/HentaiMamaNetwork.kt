package com.yenaly.han1meviewer.HentaiMama.data.remote
import com.yenaly.han1meviewer.HentaiMama.common.HentaiMamaConstants
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object HentaiMamaNetwork {

    private var _service: HentaiMamaService? = null
    private var _baseUrl: String? = null

    val baseUrl: String
        get() = Preferences.hentaiMamaBaseUrl.ifBlank { HentaiMamaConstants.BASE_URL }

    val apiUrl: String
        get() = "$baseUrl/wp-admin/admin-ajax.php"

    val service: HentaiMamaService
        get() {
            val currentBaseUrl = baseUrl
            if (_service == null || _baseUrl != currentBaseUrl) {
                _baseUrl = currentBaseUrl
                _service = createService(currentBaseUrl)
            }
            return _service!!
        }

    fun normalizeUrl(path: String): String {
        return when {
            path.startsWith("http") -> path
            path.startsWith("/") -> baseUrl + path
            else -> "$baseUrl/$path"
        }
    }

    private fun createService(baseUrl: String): HentaiMamaService {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Referer", baseUrl)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()
            .create(HentaiMamaService::class.java)
    }

    fun rebuildNetwork() {
        _service = null
        _baseUrl = null
    }
}
