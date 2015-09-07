package io.nucleo.net.proto;

import java.util.regex.Pattern;

import io.nucleo.net.HiddenServiceDescriptor;

public class HELOMessage implements Message {

  private final String peer;

  public HELOMessage(HiddenServiceDescriptor descriptor) {
    this(descriptor.getFullAddress());
  }

  private HELOMessage(String peer) {
    this.peer = peer;
  }

  public String getPeer() {
    return peer;
  }

  public String getOnionUrl() {
    return peer.split(Pattern.quote(":"))[0];
  }

  public int getPort() {
    return Integer.parseInt(peer.split(Pattern.quote(":"))[1]);
  }

  public String toString() {
    return "HELO " + peer;
  }
}
