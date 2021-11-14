package com.github.cgund98.messenger.mapper;

import com.github.cgund98.messenger.entities.ServerEntity;
import com.github.cgund98.messenger.proto.servers.MessageServer;

public class ServerMapper {

  public static ServerEntity protoToEntity(MessageServer serverProto) {
    return ServerEntity.newBuilder(serverProto.getId(), serverProto.getOwnerId())
        .setName(serverProto.getName())
        .build();
  }

  public static MessageServer entityToProto(ServerEntity serverEntity) {
    return MessageServer.newBuilder()
        .setId(serverEntity.getId())
        .setOwnerId(serverEntity.getOwnerId())
        .setName(serverEntity.getName())
        .build();
  }
}
