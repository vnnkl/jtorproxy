package io.nucleo.net;

public interface HiddenServiceReadyListener {
    public void onConnect(HiddenServiceDescriptor descriptor);
    
    public void onConnectionFailure(HiddenServiceDescriptor descriptor, Exception cause);
}
