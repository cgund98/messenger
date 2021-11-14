package com.github.cgund98.messenger.mapper;

import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.proto.users.User;

public class UserMapper {

  public static UserEntity protoToEntity(User userProto) {
    return UserEntity.newBuilder(userProto.getId()).setUsername(userProto.getUsername()).build();
  }

  public static User entityToProto(UserEntity userEntity) {
    return User.newBuilder()
        .setId(userEntity.getId())
        .setUsername(userEntity.getUsername())
        .build();
  }
}
