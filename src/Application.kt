package com.github.rwsbillyang.fileProxy

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.statement.HttpResponse
import io.ktor.features.*
import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.date.GMTDate
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


//https://github.com/ktorio/ktor-exercises/blob/master/solutions/exercise3/src/main/kotlin/Main.kt
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val cachedProxy = CachedProxy()

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ConditionalHeaders)

    install(CachingHeaders) {
        options {
            CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60 * 90), expires = null as? GMTDate?)
        }
    }

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
        get("/px/health") {
            call.respondText("OK from proxy")
        }
    }
}

data class HttpClientException(val response: HttpResponse) : IOException("HTTP Error ${response.status}")
data class HttpBadRequestException(val msg: String) : RuntimeException()

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()


