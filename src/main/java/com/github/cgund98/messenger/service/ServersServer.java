package com.github.cgund98.messenger.service;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code ServersSvc} server.
 */
public class ServersServer {
    private static final Logger logger = Logger.getLogger(ServersServer.class.getName());

    private Server server;

    private void start() throws IOException {
        int port = 8000;
        server = ServerBuilder.forPort(port)
            .addService(new ServersSvcImpl())
            .addService(ProtoReflectionService.newInstance())
            .build()
            .start();
        logger.info("Server started, listening on : " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    ServersServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server stopped");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon thread.
     * @throws InterruptedException
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Launch the server from the command line.
     * @param args
     */
    public static void main (String[] args) throws IOException, InterruptedException {
        final ServersServer server = new ServersServer();
        server.start();
        server.blockUntilShutdown();
    }

    /**
     * Implement the ServersSvc gRPC service.
     */
    public static class ServersSvcImpl extends ServersSvcGrpc.ServersSvcImplBase {

        @Override
        public void createServer(CreateServerRequest req, StreamObserver<CreateServerResponse> responseObserver) {
            CreateServerResponse response = CreateServerResponse.newBuilder()
                .setId("test")
                .setOwnerId(req.getOwnerId())
                .setName(req.getName())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}