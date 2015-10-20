package io.nucleo.net;

import java.io.File;
import java.io.IOException;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

public class JavaTorNode extends TorNode<JavaOnionProxyManager, JavaOnionProxyContext> {

    public JavaTorNode(File torDirectory) throws IOException, InstantiationException {
        super(new JavaOnionProxyManager(new JavaOnionProxyContext(torDirectory)));
    }

}
