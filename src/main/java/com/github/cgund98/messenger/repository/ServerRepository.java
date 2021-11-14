package com.github.cgund98.messenger.repository;

import com.github.cgund98.messenger.entities.ServerEntity;
import com.github.cgund98.messenger.exceptions.NotFoundException;
import com.github.cgund98.messenger.service.UsersServer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ServerRepository {
  private static final Logger logger = Logger.getLogger(UsersServer.class.getName());
  final PostgresConnection conn;

  /**
   * Construct a new ServerRepository
   *
   * @param conn - Postgres connection
   */
  public ServerRepository(PostgresConnection conn) throws SQLException {
    this.conn = conn;
    createTable();
  }

  private void createTable() throws SQLException {
    logger.info("Creating servers table in PostgreSQL if it does not currently exist...");
    Statement stmt = conn.createStatement();
    String sql =
        "CREATE TABLE IF NOT EXISTS servers("
            + "server_id SERIAL PRIMARY KEY,"
            + "owner_id INT,"
            + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),"
            + "name VARCHAR NOT NULL,"
            + "CONSTRAINT fk_owner FOREIGN KEY(owner_id) REFERENCES users(user_id)"
            + ");";
    stmt.execute(sql);
  }
  /**
   * Create a new Server object in the database
   *
   * @param userId - id of the user the new server will belong to
   * @param name - name of the new server
   * @throws SQLException - error while running SQL query
   */
  public ServerEntity create(int userId, String name) throws SQLException {
    // Prepare query
    String sql =
        "INSERT INTO servers (owner_id, name) VALUES (?, ?) RETURNING server_id, owner_id, name, created_at";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setInt(1, userId);
    stmt.setString(2, name);

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Process result
    if (!result.next()) {
      throw new SQLException("no returned result");
    }

    int serverId = result.getInt("server_id");
    int ownerId = result.getInt("owner_id");
    return ServerEntity.newBuilder(serverId, ownerId)
        .setName(result.getString("name"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }

  /**
   * Fetch a Server from the datastore by its unique ID
   *
   * @param id - server ID
   * @return - Server object with a matching ID
   * @throws NotFoundException - thrown if no user found in database with given ID
   * @throws SQLException - error while running SQL query
   */
  public ServerEntity getById(int id) throws NotFoundException, SQLException {
    // Prepare query
    String sql = "SELECT server_id, owner_id, name, created_at FROM servers WHERE server_id = ?";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setInt(1, id);

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Process result
    if (!result.next()) {
      throw new NotFoundException(String.format("No server found with ID `%d`", id));
    }

    int serverId = result.getInt("server_id");
    int ownerId = result.getInt("owner_id");
    return ServerEntity.newBuilder(serverId, ownerId)
        .setName(result.getString("name"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }

  /**
   * Fetch a list of all Servers
   *
   * @return - list of Server objects
   * @throws SQLException - error while running SQL query
   */
  public List<ServerEntity> getAll() throws SQLException {
    // Prepare query
    String sql = "SELECT server_id, owner_id, username, created_at FROM users";
    Statement stmt = conn.createStatement();

    // Execute query
    ResultSet result = stmt.executeQuery(sql);

    // Process result
    ArrayList<ServerEntity> servers = new ArrayList<>();
    while (result.next()) {
      // Read query results into ServerEntity
      int serverId = result.getInt("server_id");
      int ownerId = result.getInt("owner_id");
      ServerEntity server =
          ServerEntity.newBuilder(serverId, ownerId)
              .setName(result.getString("name"))
              .setCreatedAt(result.getTimestamp("created_at"))
              .build();

      // Append to list
      servers.add(server);
    }

    return servers;
  }

  /**
   * Persist a server object in the database.
   *
   * @param server - Server object to persist
   * @return - object that matches state in datastore
   * @throws NotFoundException - thrown if no object exists with matching ID
   * @throws SQLException - error while running SQL query
   */
  public ServerEntity save(ServerEntity server) throws NotFoundException, SQLException {
    // Prepare query
    String sql =
        "UPDATE servers SET name = ? WHERE server_id = ? RETURNING server_id, owner_id, name, created_at";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, server.getName());
    stmt.setInt(2, server.getId());

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Raise exception if query results are empty
    if (!result.next()) {
      throw new NotFoundException("no returned result");
    }

    int serverId = result.getInt("server_id");
    int ownerId = result.getInt("owner_id");
    return ServerEntity.newBuilder(serverId, ownerId)
        .setName(result.getString("name"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }
}
