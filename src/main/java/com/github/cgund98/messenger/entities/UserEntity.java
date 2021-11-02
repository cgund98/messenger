package com.github.cgund98.messenger.entities;

import java.sql.Timestamp;

public class UserEntity {

  private final int id;
  private final Timestamp createdAt;
  private String username;

  public UserEntity(int id, String username, Timestamp createdAt) {
    this.id = id;
    this.username = username;
    this.createdAt = createdAt;
  }

  public int getId() {
    return id;
  }

  public Timestamp getCreatedAt() {
    return createdAt;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
