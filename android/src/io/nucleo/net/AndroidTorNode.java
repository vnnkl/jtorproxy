package io.nucleo.net;

import java.io.IOException;

import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyContext;
import com.msopentech.thali.android.toronionproxy.AndroidOnionProxyManager;

import android.content.Context;

public class AndroidTorNode extends TorNode<AndroidOnionProxyManager, AndroidOnionProxyContext> {

  public AndroidTorNode(String torDirectoryName, Context ctx) throws IOException {
    super(new AndroidOnionProxyManager(ctx, torDirectoryName));
  }

}
