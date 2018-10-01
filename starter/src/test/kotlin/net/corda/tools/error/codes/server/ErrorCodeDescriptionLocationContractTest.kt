package net.corda.tools.error.codes.server

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import net.corda.tools.error.codes.server.commons.domain.Port
import net.corda.tools.error.codes.server.domain.*
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.test.annotations.TestBean
import net.corda.tools.error.codes.server.web.WebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.empty
import reactor.core.publisher.Flux.just
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoProcessor
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import javax.inject.Inject

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ErrorCodeDescriptionLocationContractTest.Configuration::class])
internal class ErrorCodeDescriptionLocationContractTest {

    @Inject
    private lateinit var webServer: WebServer

    private companion object {

        private var errorCoordinates: ErrorCoordinates? = null
        private var descriptions: Flux<out ErrorDescription>? = null
    }

    @Test
    @DirtiesContext
    fun found_location_is_returned_as_temporary_redirect() {

        val errorCoordinates = ErrorCoordinates(ErrorCode("123jdazz"), ReleaseVersion(4, 3, 1), PlatformEdition.OpenSource)

        val location = ErrorDescriptionLocation.External(URI.create("https://thisisatest/boom"))
        val description = ErrorDescription(location, errorCoordinates)

        val response = performRequestWithStubbedValue(errorCoordinates, just(description)).block()!!

        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.TEMPORARY_REDIRECT.code())
        assertThat(response.headers()[HttpHeaderNames.LOCATION]).isEqualTo(location.uri.toASCIIString())
    }

    @Test
    @DirtiesContext
    fun absent_location_results_in_not_found() {

        val errorCoordinates = ErrorCoordinates(ErrorCode("123jdazz"), ReleaseVersion(4, 3, 1), PlatformEdition.Enterprise)

        val response = performRequestWithStubbedValue(errorCoordinates, empty()).block()!!

        assertThat(response.statusCode()).isEqualTo(HttpResponseStatus.NOT_FOUND.code())
        assertThat(response.headers()[HttpHeaderNames.LOCATION]).isNull()
    }

    private fun performRequestWithStubbedValue(errorCoordinatesForServer: ErrorCoordinates, descriptionReturned: Flux<out ErrorDescription>): Mono<HttpResponse<Buffer>> {

        errorCoordinates = errorCoordinatesForServer
        descriptions = descriptionReturned

        val vertx = Vertx.vertx()
        val client = webServer.client(vertx)

        val promise = MonoProcessor.create<HttpResponse<Buffer>>()

        client.get(path(errorCoordinatesForServer)).followRedirects(false).send { call ->

            if (call.succeeded()) {
                promise.onNext(call.result())
            } else {
                promise.onError(call.cause())
            }
            client.close()
            vertx.close()
        }
        return promise
    }

    // This should stay hard-coded, rather than read from the actual configuration, to avoid breaking the contract without breaking the test.
    private fun path(coords: ErrorCoordinates): String = "/editions/${coords.platformEdition.description}/releases/${coords.releaseVersion.description()}/errors/${coords.code.value}"

    // TODO sollecitom get rid of this compile dependency on WebServer
    private fun WebServer.client(vertx: Vertx): WebClient = WebClient.create(vertx, WebClientOptions().setDefaultHost("localhost").setDefaultPort(options.port.value))

    @TestBean
    @ComponentScan(basePackageClasses = [ErrorCodesWebAppStarter::class], excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ErrorCodesWebAppStarter::class, WebServer.Options::class]), ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [Adapter::class, TestBean::class])])
    @SpringBootApplication
    internal open class Configuration {

        @TestBean
        @Bean
        open fun webServerOptions(): WebServer.Options {

            return ServerSocket().use {

                it.reuseAddress = true
                it.bind(InetSocketAddress(0))
                object : WebServer.Options {

                    override val port = Port(it.localPort)
                }
            }
        }

        @Adapter
        @TestBean
        @Bean
        open fun repository(): (ErrorCode, InvocationContext) -> Flux<out ErrorDescription> {

            return object : (ErrorCode, InvocationContext) -> Flux<out ErrorDescription> {

                override fun invoke(errorCode: ErrorCode, invocationContext: InvocationContext): Flux<out ErrorDescription> {

                    return descriptions!!
                }
            }
        }
    }
}