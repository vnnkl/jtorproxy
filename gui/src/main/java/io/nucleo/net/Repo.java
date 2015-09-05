package io.nucleo.net;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Repo {
    private static final Logger log = LoggerFactory.getLogger(Repo.class);


    Set<String> addresses = new HashSet<>();

    public Repo() {
    }

    public void add(String address) {
        addresses.add(address);
    }

    public Set<String> getAddresses() {
        return addresses;
    }

}
