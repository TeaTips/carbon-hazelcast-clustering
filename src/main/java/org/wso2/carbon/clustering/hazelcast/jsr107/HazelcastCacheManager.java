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

import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

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

    private String ownerTenantDomain;
    private int ownerTenantId;
    private CacheManagerFactoryImpl cacheManagerFactory;

    public HazelcastCacheManager(String name, CacheManagerFactoryImpl cacheManagerFactory) {
        this.cacheManagerFactory = cacheManagerFactory;
        CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        if (carbonContext == null) {
            throw new IllegalStateException("CarbonContext cannot be null");
        }
        ownerTenantDomain = carbonContext.getTenantDomain();
        if (ownerTenantDomain == null) {
            throw new IllegalStateException("Tenant domain cannot be " + ownerTenantDomain);
        }
        ownerTenantId = carbonContext.getTenantId();
        if (ownerTenantId == MultitenantConstants.INVALID_TENANT_ID) {
            throw new IllegalStateException("Tenant ID cannot be " + ownerTenantId);
        }
        this.name = name;
        status = Status.STARTED;
    }

    public int getOwnerTenantId() {
        return ownerTenantId;
    }

    @Override
    public String getName() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return this.name;
    }

    @Override
    public Status getStatus() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return status;
    }

    @Override
    public <K, V> CacheBuilder<K, V> createCacheBuilder(String cacheName) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        if (caches.get(cacheName) != null) {
            throw new CacheException("Cache " + cacheName + " already exists");
        }

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
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Cache<K, V> cache;
        synchronized (cacheName.intern()) {
            cache = (Cache<K, V>) caches.get(cacheName);
            if (cache == null) {
                cache = new CacheImpl<K, V>(cacheName, this);
                caches.put(cacheName, cache);
            }
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    final <K, V> Cache<K, V> getExistingCache(String cacheName) {
        Cache<K, V> cache;
        synchronized (cacheName.intern()) {
            cache = (Cache<K, V>) caches.get(cacheName);
        }
        return cache;
    }

    @Override
    public Iterable<Cache<?, ?>> getCaches() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
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
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        if (cacheName == null) {
            throw new NullPointerException("Cache name cannot be null");
        }
        CacheImpl<?, ?> oldCache;
        oldCache = (CacheImpl<?, ?>) caches.remove(cacheName);
        if (oldCache != null) {
            oldCache.stop();
        }
        cacheManagerFactory.removeCacheFromMonitoring(oldCache);
        if (caches.isEmpty()) {
            cacheManagerFactory.removeCacheManager(this, ownerTenantDomain);
        }
        return oldCache != null;
    }

    @Override
    public javax.transaction.UserTransaction getUserTransaction() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isSupported(OptionalFeature optionalFeature) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void shutdown() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
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
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        if (cls.isAssignableFrom(this.getClass())) {
            return cls.cast(this);
        }

        throw new IllegalArgumentException("Unwrapping to " + cls +
                                           " is not a supported by this implementation");
    }

    void addCache(CacheImpl cache) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        String cacheName = cache.getName();
        synchronized (cacheName.intern()) {
            caches.put(cacheName, cache);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HazelcastCacheManager that = (HazelcastCacheManager) o;

        if (ownerTenantId != that.ownerTenantId) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (ownerTenantDomain != null ? !ownerTenantDomain.equals(that.ownerTenantDomain) : that.ownerTenantDomain != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (ownerTenantDomain != null ? ownerTenantDomain.hashCode() : 0);
        result = 31 * result + ownerTenantId;
        return result;
    }
}
