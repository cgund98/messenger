package com.github.cgund98.messenger.auth;

import com.github.cgund98.messenger.entities.ServerEntity;
import com.github.cgund98.messenger.entities.UserEntity;
import com.github.cgund98.messenger.service.UsersServer;
import com.osohq.oso.Exceptions.OsoException;
import com.osohq.oso.Oso;

import java.io.IOException;
import java.util.logging.Logger;

/** Authorizer uses the Oso library to enforce ABAC across resources. */
public class Authorizer {
  private static final Logger logger = Logger.getLogger(UsersServer.class.getName());
  private final Oso oso;

  public Authorizer() throws IOException {
    oso = new Oso();
    oso.registerClass(UserEntity.class, "User");
    oso.registerClass(ServerEntity.class, "Server");
    oso.loadFiles(new String[] {"src/main/java/com/github/cgund98/messenger/auth/main.polar"});
  }

  /**
   * Check whether a user is authorized to perform a specific action on a Server resource
   *
   * @param user - actor
   * @param action - action to be performed
   * @param server - resource being acted on
   * @return - true if user has sufficient permissions
   */
  public boolean authorize(UserEntity user, String action, ServerEntity server) {
    try {
      oso.authorize(user, action, server);
      return true;
    } catch (OsoException ex) {
      logger.warning("Oso exception: " + ex.getMessage());
      return false;
    }
  }
}
