package io.nucleo.net.proto;

public enum ControlMessage implements Message {
  HEARTBEAT,
  AVAILABLE,
  HANDSHAKE_FAILED,
  ALERADY_CONNECTED,
  DISCONNECT;  
}
