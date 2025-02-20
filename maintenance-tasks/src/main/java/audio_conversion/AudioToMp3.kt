package audio_conversion

import analytics_html_img.client
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import utils.debug

object AudioToMp3 {

    suspend fun convertToMp3(
        URL: String,
        audioQuality: AudioQuality = AudioQuality.getQuality(URL.substringBeforeLast(".").takeLast(3)),
    ): String? {
        val activeHost = getHost() // ex - https://hostveryfast.onlineconverter.com/file/send
        val jobLink = convertRequest(URL, activeHost, audioQuality) // ex - https://www.onlineconverter.com/convert/309a0f2bbaeb5687b04f96b6d65b47bfdd

        // (jobStatus.contains("d")) == COMPLETION
        var jobStatus: String
        var retryCount = 40 // Set it to optimal level

        do {
            jobStatus = try {
                client.get(
                    "${activeHost.removeSuffix("send")}${jobLink.substringAfterLast("/")}"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
            retryCount--
            debug("Job Status", jobStatus)
            if (!jobStatus.contains("d")) delay(400) // Add Delay , to give Server Time to process audio
        } while (!jobStatus.contains("d", true) && retryCount != 0)

        return if (jobStatus.equals("d", true)) {
            // Return MP3 Download Link
            "${activeHost.removeSuffix("send")}${jobLink.substringAfterLast("/")}/download"
        } else null
    }

    /*
    * Response Link Ex : `https://www.onlineconverter.com/convert/11affb6d88d31861fe5bcd33da7b10a26c`
    *  - to start the conversion
    * */
    private suspend fun convertRequest(
        URL: String,
        host: String? = null,
        audioQuality: AudioQuality = AudioQuality.`320KBPS`,
    ): String {
        val activeHost = host ?: getHost()
        val res = client.submitFormWithBinaryData<String>(
            url = activeHost,
            formData = formData {
                append("class", "audio")
                append("from", "audio")
                append("to", "mp3")
                append("source", "url")
                append("url", URL.replace("https:", "http:"))
                append("audio_quality", audioQuality.kbps)
            }
        ) {
            headers {
                header("Host", activeHost.getHostDomain().also { debug(it) })
                header("Origin", "https://www.onlineconverter.com")
                header("Referer", "https://www.onlineconverter.com/")
            }
        }.run {
            debug(this)
            dropLast(3) // last 3 are useless unicode char
        }

        val job = client.get<HttpStatement>(res) {
            headers {
                header("Host", "www.onlineconverter.com")
            }
        }.execute()
        debug("Schedule Job ${job.status.isSuccess()}")
        return res
    }

    // Active Host free to process conversion
    // ex - https://hostveryfast.onlineconverter.com/file/send
    private suspend fun getHost(): String {
        return client.get<String>("https://www.onlineconverter.com/get/host") {
            headers {
                header("Host", "www.onlineconverter.com")
            }
        }.also { debug("Active Host", it) }
    }
    // Extract full Domain from URL
    // ex - hostveryfast.onlineconverter.com
    private fun String.getHostDomain(): String {
        return this.removePrefix("https://").substringBeforeLast(".") + "." + this.substringAfterLast(".").substringBefore("/")
    }
}
