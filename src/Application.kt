package com.github.rwsbillyang.fileProxy

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import java.io.IOException
//
//val client = HttpClient(Apache) {
//    engine {
//        /**
//         * Apache embedded http redirect, default = false. Obsolete by `HttpRedirect` feature.
//         * It uses the default number of redirects defined by Apache's HttpClient that is 50.
//         */
//        followRedirects = true
//
//        /**
//         * Timeouts.
//         * Use `0` to specify infinite.
//         * Negative value mean to use the system's default value.
//         */
//
//        /**
//         * Max time between TCP packets - default 10 seconds.
//         */
//        socketTimeout = 20_000
//
//        /**
//         * Max time to establish an HTTP connection - default 10 seconds.
//         */
//        connectTimeout = 10_000
//
//        /**
//         * Max time for the connection manager to start a request - 20 seconds.
//         */
//        connectionRequestTimeout = 20_000
//
//        customizeClient {
//            // this: HttpAsyncClientBuilder
//            // setProxy(HttpHost("127.0.0.1", 8080))
//
//            // Maximum number of socket connections.
//            setMaxConnTotal(40960)
//
//            // Maximum number of requests for a specific endpoint route.
//            //在请求同一个host:port时，会复用已经建立的连接,允许同一个host:port最多有几个活跃连接
//            setMaxConnPerRoute(10240)
//
//            // ...
//        }
//        customizeRequest {
//            // this: RequestConfig.Builder from Apache.
//        }
//    }
//}

val client = HttpClient(CIO) {
    install(HttpTimeout) {}


    //https://ktor.io/docs/http-client-engines.html#jvm-and-android
    engine {
        maxConnectionsCount = 20480

        endpoint {
            /**
             * Maximum number of requests for a specific endpoint route.
             */
            maxConnectionsPerRoute = 10240

            /**
             * Max size of scheduled requests per connection(pipeline queue size).
             */
            pipelineMaxSize = 20

            /**
             * Max number of milliseconds to keep idle connection alive.
             */
            keepAliveTime = 5000

            /**
             * Number of milliseconds to wait trying to connect to the server.
             */
            connectTimeout = 10000

            /**
             * Maximum number of attempts for retrying a connection.
             */
            connectAttempts = 3
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


