package com.github.cgund98.messenger.service;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.github.cgund98.messenger.auth.Authorizer;
import com.github.cgund98.messenger.auth.JwtIssuer;
import com.github.cgund98.messenger.entities.ServerEntity;
import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.exceptions.NotFoundException;
import com.github.cgund98.messenger.mapper.ServerMapper;
import com.github.cgund98.messenger.mapper.UserMapper;
import com.github.cgund98.messenger.proto.servers.*;
import com.github.cgund98.messenger.proto.users.GetUserRequest;
import com.github.cgund98.messenger.proto.users.User;
import com.github.cgund98.messenger.proto.users.UsersSvcGrpc;
import com.github.cgund98.messenger.proto.users.UsersSvcGrpc.UsersSvcBlockingStub;
import com.github.cgund98.messenger.repository.PostgresConnection;
import com.github.cgund98.messenger.repository.ServerRepository;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.*;
import io.grpc.protobuf.StatusProto;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Server that manages startup/shutdown of a {@code ServersSvc} server. */
public class ServersServer {
  private static final Logger logger = Logger.getLogger(ServersServer.class.getName());
  private Server server;
  private PostgresConnection conn;

  /**
   * Launch the server from the command line.
   *
   * @param args - command line arguments
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    final ServersServer server = new ServersServer();
    server.start();
    server.blockUntilShutdown();
  }

  private void start() throws IOException {
    // Instantiate repository
    ServerRepository serverRepository;
    try {
      conn = new PostgresConnection();
      serverRepository = new ServerRepository(conn);
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

    // Instantiate users gRPC client
    // TODO: read configuration from environment
    String target = "localhost:8000";
    logger.info(String.format("Will forward users service requests to %s...", target));
    ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    UsersSvcBlockingStub usersStub = UsersSvcGrpc.newBlockingStub(channel);

    // Instantiate other objects
    JwtIssuer issuer = new JwtIssuer();
    Authorizer authorizer = new Authorizer();
    ServersServer.ServersSvcImpl svc =
        new ServersServer.ServersSvcImpl(serverRepository, usersStub, issuer, authorizer);

    // Instantiate gRPC service
    int port = 8001;
    server =
        ServerBuilder.forPort(port)
            .addService(svc)
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
   *
   * @throws InterruptedException - keyboard interrupt
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /** Implement the ServersSvc gRPC service. */
  public static class ServersSvcImpl extends ServersSvcGrpc.ServersSvcImplBase {

    private final ServerRepository serverRepository;
    private final UsersSvcBlockingStub usersStub;
    private final JwtIssuer issuer;
    private final Authorizer authorizer;

    public ServersSvcImpl(
        ServerRepository serverRepository,
        UsersSvcBlockingStub usersStub,
        JwtIssuer issuer,
        Authorizer authorizer) {
      this.serverRepository = serverRepository;
      this.usersStub = usersStub;
      this.issuer = issuer;
      this.authorizer = authorizer;
    }

    /**
     * Parse a JWT and fetch the corresponding User object from the users gRPC service.
     *
     * @param token - encoded JWT
     * @return - User object retrieved from the
     * @throws JWTDecodeException - error decoding JWT
     * @throws StatusRuntimeException - error while querying users gRPC service
     */
    private UserEntity fetchUserFromToken(String token)
        throws JWTDecodeException, StatusRuntimeException {
      DecodedJWT decodedJwt = issuer.decode(token);

      UserEntity user;
      try {
        int userId = Integer.parseInt(decodedJwt.getSubject());
        GetUserRequest req = GetUserRequest.newBuilder().setToken(token).setId(userId).build();
        User response = usersStub.getUser(req);
        user = UserMapper.protoToEntity(response);

      } catch (NumberFormatException ex) {
        throw new JWTDecodeException("Unable to parse subject into user ID");
      }

      return user;
    }

    /**
     * gRPC endpoint for creating a new message server
     *
     * @param req - request body
     * @param responseObserver - response payload
     */
    @Override
    public void createServer(
        CreateServerRequest req, StreamObserver<MessageServer> responseObserver) {
      UserEntity user;
      ServerEntity server;
      try {
        // Parse and fetch user from JWT
        user = fetchUserFromToken(req.getToken());

        // Create server
        server = serverRepository.create(user.getId(), req.getName());

      } catch (JWTDecodeException ex) {
        // Error decoding JWT
        logger.severe(ex.getMessage());
        Status status =
            Status.newBuilder()
                .setCode(Code.UNAUTHENTICATED.getNumber())
                .setMessage(ex.getMessage())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (Exception ex) {
        // Unknown error
        logger.severe(ex.getMessage());
        Status status = Status.newBuilder().setCode(Code.INTERNAL.getNumber()).build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      MessageServer response = ServerMapper.entityToProto(server);

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for fetching a messaging server by its ID
     *
     * @param req - request body
     * @param responseObserver - response payload
     */
    @Override
    public void getServer(GetServerRequest req, StreamObserver<MessageServer> responseObserver) {
      ServerEntity server;
      try {
        // Parse and fetch user from JWT
        UserEntity user = fetchUserFromToken(req.getToken());

        // Create server
        server = serverRepository.getById(req.getId());

        // Check that user has proper authorization
        if (!authorizer.authorize(user, "read", server)) {
          Status status =
              Status.newBuilder()
                  .setCode(Code.PERMISSION_DENIED.getNumber())
                  .setMessage(
                      String.format("User not permitted to read server with ID `%d`", req.getId()))
                  .build();
          responseObserver.onError(StatusProto.toStatusRuntimeException(status));
          return;
        }

      } catch (JWTDecodeException ex) {
        // Error decoding JWT
        logger.severe(ex.getMessage());
        Status status =
            Status.newBuilder()
                .setCode(Code.UNAUTHENTICATED.getNumber())
                .setMessage(ex.getMessage())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (NotFoundException ex) {
        // No user found with requested ID
        Status status =
            Status.newBuilder()
                .setCode(Code.NOT_FOUND.getNumber())
                .setMessage(String.format("No server found with ID `%d`", req.getId()))
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (Exception ex) {
        // Unknown error
        logger.severe(ex.getMessage());
        Status status = Status.newBuilder().setCode(Code.INTERNAL.getNumber()).build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      MessageServer response = ServerMapper.entityToProto(server);

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for updating a server resource
     *
     * @param req - request body
     * @param responseObserver - response payload
     */
    @Override
    public void updateServer(
        UpdateServerRequest req, StreamObserver<MessageServer> responseObserver) {
      ServerEntity server;
      try {
        // Parse and fetch user from JWT
        UserEntity user = fetchUserFromToken(req.getToken());

        // Get ID
        server = serverRepository.getById(req.getId());

        // Update server name
        if (!req.getName().equals("")) {
          server.setName(req.getName());
        }

        // Check that user has proper authorization
        if (!authorizer.authorize(user, "update", server)) {
          Status status =
              Status.newBuilder()
                  .setCode(Code.PERMISSION_DENIED.getNumber())
                  .setMessage(
                      String.format(
                          "User not permitted to update server with ID `%d`", req.getId()))
                  .build();
          responseObserver.onError(StatusProto.toStatusRuntimeException(status));
          return;
        }

        // Persist changes
        server = serverRepository.save(server);

      } catch (JWTDecodeException ex) {
        // Error decoding JWT
        logger.severe(ex.getMessage());
        Status status =
            Status.newBuilder()
                .setCode(Code.UNAUTHENTICATED.getNumber())
                .setMessage(ex.getMessage())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (NotFoundException ex) {
        // No user found with requested ID
        Status status =
            Status.newBuilder()
                .setCode(Code.NOT_FOUND.getNumber())
                .setMessage(String.format("No server found with ID `%d`", req.getId()))
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (Exception ex) {
        // Unknown error
        logger.severe(ex.getMessage());
        Status status = Status.newBuilder().setCode(Code.INTERNAL.getNumber()).build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      MessageServer response = ServerMapper.entityToProto(server);

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for deleting a server resource
     *
     * @param req - request body
     * @param responseObserver - response payload
     */
    @Override
    public void deleteServer(
        DeleteServerRequest req, StreamObserver<DeleteServerResponse> responseObserver) {
      ServerEntity server;
      try {
        // Parse and fetch user from JWT
        UserEntity user = fetchUserFromToken(req.getToken());

        // Get ID
        server = serverRepository.getById(req.getId());

        // Check that user has proper authorization
        if (!authorizer.authorize(user, "delete", server)) {
          Status status =
              Status.newBuilder()
                  .setCode(Code.PERMISSION_DENIED.getNumber())
                  .setMessage(
                      String.format(
                          "User not permitted to update server with ID `%d`", req.getId()))
                  .build();
          responseObserver.onError(StatusProto.toStatusRuntimeException(status));
          return;
        }

        // Persist changes
        serverRepository.delete(server);

      } catch (JWTDecodeException ex) {
        // Error decoding JWT
        logger.severe(ex.getMessage());
        Status status =
            Status.newBuilder()
                .setCode(Code.UNAUTHENTICATED.getNumber())
                .setMessage(ex.getMessage())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (NotFoundException ex) {
        // No user found with requested ID
        Status status =
            Status.newBuilder()
                .setCode(Code.NOT_FOUND.getNumber())
                .setMessage(String.format("No server found with ID `%d`", req.getId()))
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (Exception ex) {
        // Unknown error
        logger.severe(ex.getMessage());
        Status status = Status.newBuilder().setCode(Code.INTERNAL.getNumber()).build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Return response
      DeleteServerResponse response = DeleteServerResponse.newBuilder().setId(req.getId()).build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    /**
     * gRPC endpoint for listing available servers
     *
     * @param req - request body
     * @param responseObserver - response payload
     */
    @Override
    public void listServers(
        ListServersRequest req, StreamObserver<ListServersResponse> responseObserver) {
      List<ServerEntity> servers;
      try {
        // Parse and fetch user from JWT
        UserEntity user = fetchUserFromToken(req.getToken());

        // Query servers
        ServerRepository.SearchOptions opts = new ServerRepository.SearchOptions(req.getName());
        servers = serverRepository.get(opts);

        // Only give back servers that a user is authorized to read
        servers.removeIf(s -> !authorizer.authorize(user, "read", s));

      } catch (JWTDecodeException ex) {
        // Error decoding JWT
        logger.severe(ex.getMessage());
        Status status =
            Status.newBuilder()
                .setCode(Code.UNAUTHENTICATED.getNumber())
                .setMessage(ex.getMessage())
                .build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;

      } catch (Exception ex) {
        // Unknown error
        ex.printStackTrace();
        logger.severe(ex.getMessage());
        Status status = Status.newBuilder().setCode(Code.INTERNAL.getNumber()).build();
        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        return;
      }

      // Build response
      ListServersResponse.Builder responseBuilder = ListServersResponse.newBuilder();
      servers.forEach(
          serverEnt -> {
            responseBuilder.addServers(ServerMapper.entityToProto(serverEnt));
          });

      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
    }
  }
}
