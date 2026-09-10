package com.cache.service;

import com.cache.dto.request.CachePutRequest;
import com.cache.dto.response.CacheEntryResponse;
import com.cache.dto.response.CacheStatsResponse;

public interface CacheService {

    CacheEntryResponse put(CachePutRequest request);

    CacheEntryResponse get(String key);

    void delete(String key);

    long clear();

    CacheStatsResponse getStats();

    void updateEvictionPolicy(String policyName);
}
