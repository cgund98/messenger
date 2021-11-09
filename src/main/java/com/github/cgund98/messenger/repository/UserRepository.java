package com.github.cgund98.messenger.repository;

import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.exceptions.NotFoundException;
import com.github.cgund98.messenger.service.UsersServer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class UserRepository {
  private static final Logger logger = Logger.getLogger(UsersServer.class.getName());
  final PostgresConnection conn;

  /**
   * Construct a new UserRepository
   *
   * @param conn - Postgres connection
   */
  public UserRepository(PostgresConnection conn) throws SQLException {
    this.conn = conn;
    createTable();
  }

  private void createTable() throws SQLException {
    logger.info("Creating users table in PostgreSQL if it does not currently exist...");
    Statement stmt = conn.createStatement();
    String sql =
        "CREATE TABLE IF NOT EXISTS users(\n"
            + "id SERIAL PRIMARY KEY,\n"
            + "username VARCHAR NOT NULL,\n"
            + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());";
    stmt.execute(sql);
  }

  /**
   * Create a new User object in the database
   *
   * @param username - Username belonging to specific user
   * @throws SQLException - error while running SQL query
   */
  public UserEntity create(String username) throws SQLException {
    // Prepare query
    String sql = "INSERT INTO users (username) VALUES (?) RETURNING id, username, created_at";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, username);

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Process result
    if (!result.next()) {
      throw new SQLException("no returned result");
    }

    return UserEntity.newBuilder(result.getInt("id"))
        .setUsername(result.getString("username"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }

  /**
   * Get a single User by their ID
   *
   * @param id - user ID
   * @return - found user object
   * @throws NotFoundException - no user found with specified user ID
   * @throws SQLException - error while running SQL query
   */
  public UserEntity getById(Integer id) throws NotFoundException, SQLException {
    // Prepare query
    String sql = "SELECT id, username, created_at FROM users WHERE id = ?";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setInt(1, id);

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Raise exception if query results are empty
    if (!result.next()) {
      throw new NotFoundException("no returned result");
    }

    return UserEntity.newBuilder(result.getInt("id"))
        .setUsername(result.getString("username"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }

  /**
   * Return a list of all users
   *
   * @return - list of users
   * @throws SQLException - error while running SQL query
   */
  public List<UserEntity> getAll() throws SQLException {
    // Prepare query
    String sql = "SELECT id, username, created_at FROM users";
    Statement stmt = conn.createStatement();

    // Execute query
    ResultSet result = stmt.executeQuery(sql);

    // Process result
    ArrayList<UserEntity> users = new ArrayList<>();
    while (result.next()) {
      // Read query results into UserEntity
      UserEntity user =
          UserEntity.newBuilder(result.getInt("id"))
              .setUsername(result.getString("username"))
              .setCreatedAt(result.getTimestamp("created_at"))
              .build();

      // Append to list
      users.add(user);
    }

    return users;
  }

  /**
   * Persist a user object in the database
   *
   * @param user - Object to persist
   * @return - updated object persisted in database
   * @throws NotFoundException - no user found with specified user ID
   * @throws SQLException - error while running SQL query
   */
  public UserEntity save(UserEntity user) throws NotFoundException, SQLException {
    // Prepare query
    String sql = "UPDATE users SET username = ? WHERE id = ? RETURNING id, username, created_at";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setString(1, user.getUsername());
    stmt.setInt(2, user.getId());

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Raise exception if query results are empty
    if (!result.next()) {
      throw new NotFoundException("no returned result");
    }

    return UserEntity.newBuilder(result.getInt("id"))
        .setUsername(result.getString("username"))
        .setCreatedAt(result.getTimestamp("created_at"))
        .build();
  }
}
