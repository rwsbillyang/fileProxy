package com.github.rwsbillyang.fileProxy


import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern


class FileInfo(val relativeDir: String, val filename: String, val extName: String = "")

/**
 * 基于File的cache 请求file download代理
 * */
class CachedProxy {
    companion object {
        private const val CacheDir = "cache/"
        //private const val CacheDir = "/home/www/fileProxy/cache/"
    }

    private val log = LoggerFactory.getLogger("FileRelay")

    //https://mp.weixin.qq.com/s/4HBgFyxQr3Fyzam5CjLmkw http://puui.qpic.cn/vpic/0/q3160lrh9sg.png/0
    //https://mp.weixin.qq.com/s/GShHXGJDzAtw0VNQTVzjyQ https://vpic.video.qq.com/9492804/l0669n6emgf.png
    private val reg = "url=http(s)?://.*\\.(qpic|qlogo|qq)\\.(cn|com)/.+?"
    //private val reg = "url=http(s)?://(mmbiz|mmsns)\\.(qpic|qlogo)\\.cn/.+?"
    /**
     * 下载时可能是并发，只有第一个到达的请求才会去真正下载
     * */
    private val downloadTasks = ConcurrentHashMap<String, Deferred<ByteArrayContent>>()

    suspend fun taskNum(call: ApplicationCall){
        call.respondText(downloadTasks.size.toString(), ContentType.Text.Plain, HttpStatusCode.OK)
    }

    suspend fun doProxy(call: ApplicationCall) {
        val url = call.request.queryParameters["url"]

        /*=============合法性检查=============*/
        if (url.isNullOrBlank()) {
            throw HttpBadRequestException("wrong parameter")
        }
        val queryStr: String = call.request.queryString()
        val valid: Boolean = Pattern.matches(reg, queryStr)
        if (!valid) {
            log.warn("not match reg RequestURL:{}, {} ", call.request.uri, queryStr)
            throw HttpBadRequestException("not support url")
        }

        /*=============抽取文件信息=============*/
        val info = extractFileInfoFromUrl(url)

        /*=============根据文件信息判断是否存在，若不存在尚需下载=============*/
        val absoluteDir: String = StringBuffer(CacheDir).append(info.relativeDir).toString()
        val absoluteFullName =
            StringBuffer(absoluteDir).append("/").append(info.filename).append(".").append(info.extName).toString()

        val file = File(absoluteFullName)
        if (file.exists()) {
            //log.info("read local: $url")
            call.respondFile(file)
        } else {
            val content = downloadFromUrlAsync(url, absoluteFullName, absoluteDir).await()
            call.respondBytes(content.bytes(),content.contentType,content.status)
        }
    }


    /**
     * 下载数据到文件
     * 注意：并发问题  相同的请求同时到来时，只需第一个请求去download下载，其它请求只需等待然后被唤醒
     *
     * @param url 待下载的文件链接
     * @param absoluteFilename 待保存的文件路径及名称
     * @param absoluteDir 保存到何处文件夹中，没有的话需先创建
     *
     * TODO: save img file into redis or file server
     * */
    private suspend fun downloadFromUrlAsync(
        url: String,
        absoluteFilename: String,
        absoluteDir: String
    ): Deferred<ByteArrayContent> = coroutineScope {
        val value = downloadTasks[url]
        if (value != null) {
            value
        } else {
            val fileDir = File(absoluteDir)
            if (!fileDir.exists()) fileDir.mkdirs()

            val deferred = async {
                val response: HttpResponse = client.get(url)

                if (!response.status.isSuccess()) {
                    log.info("download fail: $url")
                    downloadTasks.remove(url)
                    throw HttpClientException(response)
                }else{
                    val content = ByteArrayContent(response.readBytes())

                    launch {
                        val file = File(absoluteFilename)
                        file.writeBytes(content.bytes())
                        downloadTasks.remove(url)
                    }
                    content
                }
            }

            downloadTasks[url] = deferred
            deferred
        }
    }


    /**
     * 从url中抽取文件信息
     * TODO: 使用正则表达式抽取
     * */
    private fun extractFileInfoFromUrl(url: String): FileInfo {
        //int index = url.indexOf('/', url.indexOf("//")+2);
        var relative = url.substring(url.indexOf("//") + 3)

        //获取扩展名信息
        val extIndex = relative.lastIndexOf("wx_fmt=")
        var extName: String? = null
        if (extIndex < 0) { // 某些链接中无扩展名，即没有wx_fmt=
            //log.info("no ext nmae, url=$url")
        } else {
            val questionMarkIndex = relative.indexOf('?', extIndex + 7) //wx_fmt=jpg?xxxxx
            extName = if (questionMarkIndex < 0) relative.substring(extIndex + 7) //length of "wx_fmt="
            else relative.substring(extIndex + 7, questionMarkIndex) //length of "wx_fmt="
        }
        var endIndex = relative.indexOf('?')
        if (endIndex > 0) {
            relative = relative.substring(0, endIndex) //若问号存在的话，取问号前面的字符串，后面的丢弃
        }
        endIndex = relative.lastIndexOf('/')
        relative = relative.substring(0, endIndex) //最后一个/后面的字符丢弃

        val dirIndex = relative.lastIndexOf('/')
        val dir = relative.substring(0, dirIndex)
        val filename = relative.substring(dirIndex + 1)

        return FileInfo(dir, filename, extName ?: "jpg")
    }

}