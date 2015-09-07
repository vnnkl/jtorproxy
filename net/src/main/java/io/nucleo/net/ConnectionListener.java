package io.nucleo.net;

import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.exceptions.ConnectionException;

public interface ConnectionListener {

  public abstract void onMessage(Connection con, ContainerMessage msg);

  public void onDisconnect(Connection con);

  public void onTimeout(Connection con);

  public void onError(Connection con, ConnectionException e);

  public void onReady(Connection con);

}
