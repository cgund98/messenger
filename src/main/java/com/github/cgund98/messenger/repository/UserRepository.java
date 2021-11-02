package com.github.cgund98.messenger.repository;

import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.exceptions.NotFoundException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UserRepository {
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
   * @throws SQLException
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

    return new UserEntity(
        result.getInt("id"), result.getString("username"), result.getTimestamp("created_at"));
  }

  public UserEntity getById(Integer id) throws NotFoundException, SQLException {
    // Prepare query
    String sql = "SELECT id, username, created_at FROM users WHERE id = ?";
    PreparedStatement stmt = conn.prepareStatement(sql);
    stmt.setInt(1, id);

    // Execute query
    ResultSet result = stmt.executeQuery();

    // Process result
    if (!result.next()) {
      throw new NotFoundException("no returned result");
    }

    return new UserEntity(
        result.getInt("id"), result.getString("username"), result.getTimestamp("created_at"));
  }
}
