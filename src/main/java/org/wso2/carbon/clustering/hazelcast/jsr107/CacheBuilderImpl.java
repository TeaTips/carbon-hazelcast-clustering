/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.clustering.hazelcast.jsr107;

import javax.cache.Cache;
import javax.cache.CacheBuilder;
import javax.cache.CacheConfiguration;
import javax.cache.CacheLoader;
import javax.cache.CacheWriter;
import javax.cache.Caching;
import javax.cache.InvalidConfigurationException;
import javax.cache.OptionalFeature;
import javax.cache.event.CacheEntryListener;
import javax.cache.transaction.IsolationLevel;
import javax.cache.transaction.Mode;

/**
 * TODO: class description
 */
public class CacheBuilderImpl<K, V> implements CacheBuilder<K, V> {

    private CacheConfigurationImpl cacheConfiguration = new CacheConfigurationImpl();
    private String cacheName;
    private HazelcastCacheManager cacheManager;
    private CacheImpl<K, V> cache;

    public CacheBuilderImpl(String cacheName, HazelcastCacheManager cacheManager) {
        this.cacheName = cacheName;
        this.cacheManager = cacheManager;
    }

    @Override
    public Cache<K, V> build() {
        synchronized (cacheName.intern()) {
            cache = (CacheImpl<K, V>) cacheManager.getExistingCache(cacheName);
            if (cache == null) {
                cache = new CacheImpl<K, V>(cacheName, cacheManager);
                //TODO: set the tenant info
                cache.setCacheConfiguration(cacheConfiguration);
                cacheManager.addCache(cache);
            }
        }
        return cache;
    }

    @Override
    public CacheBuilder<K, V> setCacheLoader(CacheLoader<K, ? extends V> cacheLoader) {
        cacheConfiguration.setCacheLoader(cacheLoader);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setCacheWriter(CacheWriter<? super K, ? super V> cacheWriter) {
        cacheConfiguration.setCacheWriter(cacheWriter);
        return this;
    }

    @Override
    public CacheBuilder<K, V> registerCacheEntryListener(CacheEntryListener<K, V> cacheEntryListener) {
        cache.registerCacheEntryListener(cacheEntryListener);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setStoreByValue(boolean storeByValue) {
        if (!storeByValue && !Caching.isSupported(OptionalFeature.STORE_BY_REFERENCE)) {
            throw new InvalidConfigurationException("storeByValue");
        }
        cacheConfiguration.setStoreByValue(storeByValue);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setTransactionEnabled(IsolationLevel isolationLevel, Mode mode) {
        if (!Caching.isSupported(OptionalFeature.TRANSACTIONS)) {
            throw new InvalidConfigurationException("transactionsEnabled");
        }
        cacheConfiguration.setTransactionMode(mode);
        cacheConfiguration.setIsolationLevel(isolationLevel);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setStatisticsEnabled(boolean enableStatistics) {
        cacheConfiguration.setStatisticsEnabled(enableStatistics);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setReadThrough(boolean readThrough) {
        cacheConfiguration.setReadThrough(readThrough);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setWriteThrough(boolean writeThrough) {
        cacheConfiguration.setWriteThrough(writeThrough);
        return this;
    }

    @Override
    public CacheBuilder<K, V> setExpiry(CacheConfiguration.ExpiryType type,
                                        CacheConfiguration.Duration duration) {
        if (type == null) {
            throw new NullPointerException("ExpiryType cannot be null");
        }
        if (duration == null) {
            throw new NullPointerException("Duration cannot be null");
        }
        cacheConfiguration.setExpiry(duration.getDurationAmount(), duration.getTimeUnit(), type);
        return this;
    }
}
