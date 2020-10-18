package com.github.rwsbillyang.fileProxy

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.IOException

val client = HttpClient(Apache) {
    engine {
        /**
         * Apache embedded http redirect, default = false. Obsolete by `HttpRedirect` feature.
         * It uses the default number of redirects defined by Apache's HttpClient that is 50.
         */
        followRedirects = true

        /**
         * Timeouts.
         * Use `0` to specify infinite.
         * Negative value mean to use the system's default value.
         */

        /**
         * Max time between TCP packets - default 10 seconds.
         */
        socketTimeout = 20_000

        /**
         * Max time to establish an HTTP connection - default 10 seconds.
         */
        connectTimeout = 10_000

        /**
         * Max time for the connection manager to start a request - 20 seconds.
         */
        connectionRequestTimeout = 20_000

        customizeClient {
            // this: HttpAsyncClientBuilder
            // setProxy(HttpHost("127.0.0.1", 8080))

            // Maximum number of socket connections.
            setMaxConnTotal(1000)

            // Maximum number of requests for a specific endpoint route.
            setMaxConnPerRoute(100)

            // ...
        }
        customizeRequest {
            // this: RequestConfig.Builder from Apache.
        }
    }
}


//https://github.com/ktorio/ktor-samples/blob/master/generic/samples/reverse-proxy/src/ReverseProxyApplication.kt
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val cachedProxy = CachedProxy()

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
   // install(ConditionalHeaders)



    install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
    install(XForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy

    install(StatusPages) {
        exception<AuthenticationException> { cause ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { cause ->
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<HttpBadRequestException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.msg)
        }

    }
    routing {
        get("/image.php") {
            cachedProxy.doProxy(call)
        }
        get("/px/img") {
            cachedProxy.doProxy(call)
        }
        get("/px/taskNum") {
            cachedProxy.taskNum(call)
        }
        get("/") {
            call.respondText("OK from proxy")
        }
    }
}

data class HttpClientException(val response: HttpResponse) : IOException("HTTP Error ${response.status}")
data class HttpBadRequestException(val msg: String) : RuntimeException()

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()


