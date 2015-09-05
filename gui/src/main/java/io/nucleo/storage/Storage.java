package io.nucleo.storage;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    protected Map<String, Serializable> map = new HashMap<>();

    public Storage() {
    }

    public Serializable put(String key, Serializable value) {
        return map.put(key, value);
    }

    public Set<Serializable> add(String key, Serializable value) {
        Serializable entry = map.get(key);
        HashSet<Serializable> set;
        if (entry == null) set = new HashSet<>();
        else if (entry instanceof HashSet) set = (HashSet<Serializable>) entry;
        else return null;
        set.add(value);
        map.put(key, set);
        return set;
    }

    public Serializable get(String key) {
        return map.get(key);
    }
}
