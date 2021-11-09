package com.github.cgund98.messenger.service;

import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.exceptions.NotFoundException;
import com.github.cgund98.messenger.proto.*;
import com.github.cgund98.messenger.repository.PostgresConnection;
import com.github.cgund98.messenger.repository.UserRepository;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Server that manages startup/shutdown of a {@code UsersSvc} server. */
public class UsersServer {
  private static final Logger logger = Logger.getLogger(UsersServer.class.getName());
  private Server server;
  private PostgresConnection conn;

  /**
   * Launch the server from the command line.
   *
   * @param args
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final UsersServer server = new UsersServer();
    server.start();
    server.blockUntilShutdown();
  }

  private void start() throws IOException {
    // Instantiate repository
    UserRepository userRepository;
    try {
      conn = new PostgresConnection();
      userRepository = new UserRepository(conn);
    } catch (SQLException e) {
      logger.severe(e.getMessage());

      // Close Postgres connection
      try {
        conn.close();
      } catch (SQLException ex) {
        logger.severe(ex.getMessage());
      }
      return;
    }

    // Start gRPC server
    int port = 8000;
    server =
        ServerBuilder.forPort(port)
            .addService(new UsersSvcImpl(userRepository))
            .addService(ProtoReflectionService.newInstance())
            .build()
            .start();
    logger.info("Server started, listening on : " + port);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                  UsersServer.this.stop();
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

      // Close Postgres connection
      try {
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException ex) {
        logger.severe(ex.getMessage());
      }
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon thread.
   *
   * @throws InterruptedException
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /** Implement the UsersSvc gRPC service. */
  public static class UsersSvcImpl extends UsersSvcGrpc.UsersSvcImplBase {

    private final UserRepository userRepository;

    public UsersSvcImpl(UserRepository userRepository) {
      this.userRepository = userRepository;
    }

    /**
     * gRPC endpoint for listing all Users
     *
     * @param req - Request payload
     * @param responseObserver - Response
     */
    @Override
    public void listUsers(
        ListUsersRequest req, StreamObserver<ListUsersResponse> responseObserver) {

      List<UserEntity> users;

      try {
        // Make query
        users = userRepository.getAll();

      } catch (SQLException e) {
        // Unknown SQL error
        logger.severe(e.getMessage());
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.INTERNAL.getNumber())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Build response from list of users
      ListUsersResponse.Builder responseBuilder = ListUsersResponse.newBuilder();
      users.forEach(
          (userEnt) -> {
            User user =
                User.newBuilder().setId(userEnt.getId()).setUsername(userEnt.getUsername()).build();
            responseBuilder.addUsers(user);
          });

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }

    /** gRPC endpoint for updating a user */
    @Override
    public void updateUser(UpdateUserRequest req, StreamObserver<User> responseObserver) {

      // Ensure there is at least one field to update
      if (req.getUsername().equals("")) {
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.INVALID_ARGUMENT.getNumber())
                .setMessage("Must specify at least one field to update.")
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      UserEntity user;
      try {
        // Get current persisted object
        user = userRepository.getById(req.getId());

        // Update username
        if (!req.getUsername().equals("")) {
          user.setUsername(req.getUsername());
        }

        // Persist changes
        user = userRepository.save(user);

      } catch (NotFoundException e) {
        // No user found with requested ID
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.NOT_FOUND.getNumber())
                .setMessage(String.format("No user found with ID `%d`", req.getId()))
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (SQLException e) {
        // Unknown SQL error
        logger.severe(e.getMessage());
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.INTERNAL.getNumber())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      User response = User.newBuilder().setId(user.getId()).setUsername(user.getUsername()).build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for creating a new User
     *
     * @param req - Request payload
     * @param responseObserver - Response
     */
    @Override
    public void createUser(CreateUserRequest req, StreamObserver<User> responseObserver) {

      UserEntity user;
      try {
        user = userRepository.create(req.getUsername());
      } catch (SQLException e) {
        // Unknown SQL error
        logger.severe(e.getMessage());
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.INTERNAL.getNumber())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      User response = User.newBuilder().setId(user.getId()).setUsername(user.getUsername()).build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for fetching a User by its ID
     *
     * @param req - Request payload
     * @param responseObserver - Response
     */
    @Override
    public void getUser(GetUserRequest req, StreamObserver<User> responseObserver) {

      UserEntity user;
      try {
        // Make query
        user = userRepository.getById(req.getId());

      } catch (NotFoundException e) {
        // No user found with requested ID
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.NOT_FOUND.getNumber())
                .setMessage(String.format("No user found with ID `%d`", req.getId()))
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (SQLException e) {
        // Unknown SQL error
        logger.severe(e.getMessage());
        com.google.rpc.Status status =
            com.google.rpc.Status.newBuilder()
                .setCode(com.google.rpc.Code.INTERNAL.getNumber())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      User response = User.newBuilder().setId(user.getId()).setUsername(user.getUsername()).build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }
}
