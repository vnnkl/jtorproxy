package io.nucleo.net.proto;

public enum NetMessage implements Message {
  HEARTBEAT,
  AVAILABLE,
  HANDSHAKE_FAILED,
  DISCONNECT;
  
}
