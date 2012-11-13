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
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.OptionalFeature;
import javax.cache.Status;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO: class description
 */
public class HazelcastCacheManager implements CacheManager {
    private Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<String, Cache<?, ?>>();
    private volatile Status status;
    private String name;

    public HazelcastCacheManager(String name) {
        this.name = name;
        status = Status.STARTED;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public <K, V> CacheBuilder<K, V> createCacheBuilder(String cacheName) {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        if (caches.get(cacheName) != null) {
            throw new CacheException("Cache " + cacheName + " already exists");
        }

        //TODO: where did these naming constraints come from?
        if (cacheName == null) {
            throw new NullPointerException("A cache name must must not be null.");
        }
        Pattern searchPattern = Pattern.compile("\\S+");
        Matcher matcher = searchPattern.matcher(cacheName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("A cache name must contain one or more non-whitespace characters");
        }

        return new CacheBuilderImpl<K, V>(cacheName, this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String cacheName) {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Cache<K, V> cache = (Cache<K, V>) caches.get(cacheName);
        if(cache == null){
            cache = new CacheImpl<K, V>(cacheName, this);
            caches.put(cacheName, cache);
        }
        return cache;
    }

    @Override
    public Iterable<Cache<?, ?>> getCaches() {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        HashSet<Cache<?, ?>> set = new HashSet<Cache<?, ?>>();
        for (Cache<?, ?> cache : caches.values()) {
            set.add(cache);
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public boolean removeCache(String cacheName) {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        if (cacheName == null) {
            throw new NullPointerException();
        }
        Cache<?, ?> oldCache;
        oldCache = caches.remove(cacheName);
        if (oldCache != null) {
            oldCache.stop();
        }

        return oldCache != null;
    }

    @Override
    public javax.transaction.UserTransaction getUserTransaction() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isSupported(OptionalFeature optionalFeature) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void shutdown() {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        for (Cache<?, ?> cache : caches.values()) {
            try {
                cache.stop();
            } catch (Exception ignored) {
            }
        }
        caches.clear();
        this.status = Status.STOPPED;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    boolean isEmpty() {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        return caches.isEmpty();
    }

    void addCache(CacheImpl cache) {
        //TODO: Check/set tenant info
        /*CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if(carbonContext == null){
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        */
        caches.put(cache.getName(), cache);
    }
}
