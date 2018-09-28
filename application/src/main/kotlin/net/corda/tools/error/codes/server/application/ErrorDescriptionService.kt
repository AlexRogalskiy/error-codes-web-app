package net.corda.tools.error.codes.server.application

import net.corda.tools.error.codes.server.commons.identity.set
import net.corda.tools.error.codes.server.commons.events.AbstractEvent
import net.corda.tools.error.codes.server.commons.events.EventId
import net.corda.tools.error.codes.server.commons.events.EventPublisher
import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorCoordinates
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.InvocationContext
import net.corda.tools.error.codes.server.domain.PlatformEdition
import net.corda.tools.error.codes.server.domain.ReleaseVersion
import org.apache.commons.lang3.builder.ToStringBuilder
import reactor.core.publisher.Mono

interface ErrorDescriptionService : EventPublisher<ErrorDescriptionService.Event>, AutoCloseable {

    fun descriptionLocationFor(errorCode: ErrorCode, releaseVersion: ReleaseVersion, platformEdition: PlatformEdition, invocationContext: InvocationContext): Mono<ErrorDescriptionLocation>

    sealed class Event(id: EventId = EventId.newInstance()) : AbstractEvent(id) {

        sealed class Invocation(val invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event(id) {

            override fun appendToStringElements(toString: ToStringBuilder) {

                super.appendToStringElements(toString)
                toString["invocationContext"] = invocationContext.description
            }

            sealed class Completed(invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation(invocationContext, id) {

                sealed class DescriptionLocationFor(val errorCoordinates: ErrorCoordinates, val location: ErrorDescriptionLocation?, invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation.Completed(invocationContext, id) {

                    class WithoutDescriptionLocation(errorCoordinates: ErrorCoordinates, invocationContext: InvocationContext, id: EventId = EventId.newInstance()) : ErrorDescriptionService.Event.Invocation.Completed.DescriptionLocationFor(errorCoordinates, null, invocationContext, id)

                    override fun appendToStringElements(toString: ToStringBuilder) {

                        super.appendToStringElements(toString)
                        toString["errorCode"] = errorCoordinates.code.value
                        toString["releaseVersion"] = errorCoordinates.releaseVersion.description()
                        toString["platformEdition"] = errorCoordinates.platformEdition.description
                        toString["location"] = when (location) {
                            is ErrorDescriptionLocation.External -> location.uri
                            null -> "<null>"
                        }
                    }
                }
            }
        }
    }
}