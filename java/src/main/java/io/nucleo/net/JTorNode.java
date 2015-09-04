package io.nucleo.net;

import java.io.File;
import java.io.IOException;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

import oi.nucleo.net.TorNode;

public class JTorNode extends TorNode<JavaOnionProxyManager, JavaOnionProxyContext> {

    public JTorNode(File torDirectory) throws IOException {
        super(torDirectory);
    }

}
