package com.github.cgund98.messenger.repository;

import java.sql.*;

/** Extraction of a connection to a local Derby database. */
public class PostgresConnection {

  final Connection conn;

  public PostgresConnection() throws SQLException {
    // TODO: Read credentials from environment
    String url = "jdbc:postgresql://localhost:5432/postgres";
    String user = "postgres";
    String password = "postgres";

    conn = DriverManager.getConnection(url, user, password);
  }

  public Statement createStatement() throws SQLException {
    return conn.createStatement();
  }

  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return conn.prepareStatement(sql);
  }

  public CallableStatement prepareCall(String sql) throws SQLException {
    return conn.prepareCall(sql);
  }

  public void close() throws SQLException {
    conn.close();
  }
}
