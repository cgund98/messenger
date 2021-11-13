package com.github.cgund98.messenger.entities;

import java.sql.Timestamp;

public class ServerEntity {
  // Certain fields must be public so Oso can read them.
  public final int id;
  public final int ownerId;

  private final Timestamp createdAt;
  private String name;

  private ServerEntity(Builder builder) {
    this.id = builder.id;
    this.ownerId = builder.ownerId;
    this.createdAt = builder.createdAt;
    this.name = builder.name;
  }

  public static Builder newBuilder(int id, int ownerId) {
    return new Builder(id, ownerId);
  }

  /** Getters and setters */
  public int getId() {
    return id;
  }

  public int getOwnerId() {
    return ownerId;
  }

  public Timestamp getCreatedAt() {
    return createdAt;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /** Builder helper class */
  public static class Builder {
    private final int id;
    private final int ownerId;
    private Timestamp createdAt;
    private String name;

    public Builder(int id, int ownerId) {
      this.id = id;
      this.ownerId = ownerId;
    }

    public Builder setCreatedAt(Timestamp createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public ServerEntity build() {
      return new ServerEntity(this);
    }
  }
}
