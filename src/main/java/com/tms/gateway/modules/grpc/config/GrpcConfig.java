package com.tms.gateway.modules.grpc.config;

import com.tms.gateway.grpc.user.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC channel and stub configuration for microservice connections.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcConfig {

    private final GrpcProperties props;

    private ManagedChannel userChannel;

    @Bean
    public ManagedChannel userChannel() {
        var endpoint = props.getServices().getUser();
        userChannel = ManagedChannelBuilder.forAddress(endpoint.getHost(), endpoint.getPort()).usePlaintext() // TLS
                                                                                                              // handled
                                                                                                              // at
                                                                                                              // ingress
                                                                                                              // level
                                                                                                              // in k8s
                .build();
        log.info("Created gRPC channel to user-service at {}:{}", endpoint.getHost(), endpoint.getPort());
        return userChannel;
    }

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceStub(ManagedChannel userChannel) {
        return UserServiceGrpc.newBlockingStub(userChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (userChannel != null && !userChannel.isShutdown()) {
            try {
                userChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
