package io.nucleo.net.node;

import io.nucleo.net.proto.ContainerMessage;

public interface MessageListener {

  public abstract void onMessage(Connection con, ContainerMessage msg);
  
  public void onDisconnect(Connection con);
  
  public void onTimeout(Connection con);
}
