package com.cache.persistence;

import com.cache.model.CacheEntry;

import java.util.Collection;
import java.util.List;

public interface PersistenceService {

    int saveSnapshot(Collection<CacheEntry> entries);

    List<CacheEntry> loadSnapshot();
}
