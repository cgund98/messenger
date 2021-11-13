package com.github.cgund98.messenger.entities;

import java.sql.Timestamp;

public class UserEntity {
  // Certain fields must be public so Oso can read them.
  public final int id;

  private final Timestamp createdAt;
  private String username;

  private UserEntity(Builder builder) {
    this.id = builder.id;
    this.username = builder.username;
    this.createdAt = builder.createdAt;
  }

  public static Builder newBuilder(int id) {
    return new Builder(id);
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

  /** Builder helper class */
  public static class Builder {
    private final int id;
    private Timestamp createdAt;
    private String username;

    public Builder(int id) {
      this.id = id;
    }

    public Builder setCreatedAt(Timestamp createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder setUsername(String username) {
      this.username = username;
      return this;
    }

    public UserEntity build() {
      return new UserEntity(this);
    }
  }
}
