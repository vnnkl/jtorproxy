package io.nucleo.net.proto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import io.nucleo.net.HiddenServiceDescriptor;

public class IDMessage implements Message {

  private static SecureRandom rnd;

  static {
    try {
      rnd = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  private final String id;
  private final long   nonce;

  public IDMessage(HiddenServiceDescriptor descriptor) {
    this(descriptor.getFullAddress(), rnd.nextLong());
  }

  private IDMessage(String id, long nonce) {
    this.id = id;
    this.nonce = nonce;
  }

  public String getPeer() {
    return id;
  }

  public IDMessage reply() {
    return new IDMessage(id, nonce << 1);
  }

  public boolean verify(IDMessage msg) {
    return id.equals(msg.id) && ((nonce << 1) == msg.nonce);
  }

  public String toString() {
    return "ID " + id;
  }
}
