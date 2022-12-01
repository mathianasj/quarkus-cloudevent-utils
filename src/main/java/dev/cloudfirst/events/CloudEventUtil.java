package dev.cloudfirst.events;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.reactivemessaging.http.runtime.OutgoingHttpMetadata;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadataBuilder;
import io.smallrye.reactive.messaging.providers.locals.ContextAwareMessage;

@ApplicationScoped
public class CloudEventUtil {
    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "service.source")
    String serviceSource;

    @Channel("default-broker")
    Emitter<Object> cloudEventEmitter;
    
    public CloudEventUtil() {}
    
    public CloudEventUtil(ObjectMapper mapper, String serviceSource, Emitter<Object> cloudEventEmitter) {
        this.mapper = mapper;
        this.serviceSource = serviceSource;
        this.cloudEventEmitter = cloudEventEmitter;
    }

    public Uni<Void> sendEvent(Object record, String id) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Make it a cloud event
        OutgoingCloudEventMetadata<Object> cloudEventMetadata = new OutgoingCloudEventMetadataBuilder<Object>()
            .withSource(URI.create(serviceSource))
            .withType(record.getClass().getName())
            .withExtension("partitionkey", id)
            .build();
 
        // Make sure we have the right content type header
        OutgoingHttpMetadata httpMetaData = new OutgoingHttpMetadata.Builder().addHeader("content-type", MediaType.APPLICATION_JSON).build();

        // Setup the message with ack / nack to be passed on as reactive
        Message<Object> msg = ContextAwareMessage.of(record)
        .addMetadata(httpMetaData)
        .addMetadata(cloudEventMetadata)
        .withAck(() -> {
            System.out.println("got an complete");
            future.complete(null);
            return CompletableFuture.completedFuture(null);
        }).withNack(reason -> {
            System.out.println("got an error");
            future.completeExceptionally(reason);
            return CompletableFuture.completedFuture(null);
        });

        // emit the message
        cloudEventEmitter.send(msg);

        // return the future so the chain can be monitored
        return Uni.createFrom().completionStage(future);
    }

}
