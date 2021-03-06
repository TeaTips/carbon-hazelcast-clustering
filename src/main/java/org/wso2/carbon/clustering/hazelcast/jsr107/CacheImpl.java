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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.clustering.hazelcast.HazelcastInstanceManager;
import org.wso2.carbon.clustering.hazelcast.jsr107.eviction.EvictionAlgorithm;
import org.wso2.carbon.clustering.hazelcast.jsr107.eviction.EvictionUtil;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheLoader;
import javax.cache.CacheManager;
import javax.cache.CacheStatistics;
import javax.cache.Status;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryReadListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.mbeans.CacheMXBean;
import javax.cache.transaction.IsolationLevel;
import javax.cache.transaction.Mode;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * TODO: class description
 * <p/>
 * TODO: Cache statistics
 */
@SuppressWarnings("unchecked")
public class CacheImpl<K, V> implements Cache<K, V> {
    private static final Log log = LogFactory.getLog(CacheImpl.class);
    private static final int CACHE_LOADER_THREADS = 2;
    private static final long MAX_CACHE_IDLE_TIME_MILLIS = 15 * 60 * 1000; // 15mins
    private static final long DEFAULT_CACHE_EXPIRY_MINS = 15;
    private static final long DEFAULT_CACHE_EXPIRY_MILLIS = DEFAULT_CACHE_EXPIRY_MINS * 60 * 1000;

    private String cacheName;
    private CacheManager cacheManager;
    private boolean isLocalCache;
    private IMap<K, CacheEntry<K, V>> distributedCache;
    private Map<K, CacheEntry<K, V>> localCache;
    private CacheConfiguration<K, V> cacheConfiguration;

    private List<CacheEntryListener> cacheEntryListeners = new ArrayList<CacheEntryListener>();
    private Status status;
    private CacheStatisticsImpl cacheStatistics;
    private ObjectName cacheMXBeanObjName;
    private final ExecutorService cacheLoadExecService = Executors.newFixedThreadPool(CACHE_LOADER_THREADS);

    private String ownerTenantDomain;
    private int ownerTenantId;
    private long lastAccessed = System.currentTimeMillis();

    private long capacity = CachingConstants.DEFAULT_CACHE_CAPACITY;
    private EvictionAlgorithm evictionAlgorithm = CachingConstants.DEFAULT_EVICTION_ALGORITHM;

    public CacheImpl(String cacheName, CacheManager cacheManager) {
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
        this.cacheName = cacheName;
        this.cacheManager = cacheManager;
        HazelcastInstance hazelcastInstance =
                HazelcastInstanceManager.getInstance().getHazelcastInstance();
        if (hazelcastInstance != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using Hazelcast based distributed cache");
            }
            distributedCache = hazelcastInstance.getMap("$cache.$domain[" + ownerTenantDomain + "]" +
                                                        cacheManager.getName() + "#" + cacheName);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Using local cache");
            }
            isLocalCache = true;
            localCache = new ConcurrentHashMap<K, CacheEntry<K, V>>();
        }
        cacheStatistics = new CacheStatisticsImpl();
        registerMBean();
        CacheManagerFactoryImpl.addCacheForMonitoring(this);
        status = Status.STARTED;
    }

    private MBeanServer getMBeanServer() {
        MBeanServer mserver;
        if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
            mserver = MBeanServerFactory.findMBeanServer(null).get(0);
        } else {
            mserver = MBeanServerFactory.createMBeanServer();
        }
        return mserver;
    }

    private void registerMBean() {
        String serverPackage = "org.wso2.carbon";
        try {
            String objectName = serverPackage + ":type=Cache,tenant=" + ownerTenantDomain +
                                ",manager=" + cacheManager.getName() + ",name=" + cacheName;
            MBeanServer mserver = getMBeanServer();
            cacheMXBeanObjName = new ObjectName(objectName);
            Set set = mserver.queryNames(new ObjectName(objectName), null);
            if (set.isEmpty()) {
                CacheMXBeanImpl cacheMXBean =
                        new CacheMXBeanImpl(this, ownerTenantDomain, ownerTenantId);
                mserver.registerMBean(cacheMXBean, cacheMXBeanObjName);
            }
        } catch (Exception e) {
            String msg = "Could not register CacheMXBeanImpl MBean";
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<K, CacheEntry<K, V>> getMap() {
        return isLocalCache ? localCache : distributedCache;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        CacheEntry entry = map.get(key);
        V value = null;
        if (entry != null) {
            value = (V) entry.getValue();
            map.put(key, entry); // Need to put this back so that the accessed timestamp change is visible throughout the cluster
            notifyCacheEntryRead(key, value);
        }
        return value;
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> source = getMap();
        Map<K, V> destination = new HashMap<K, V>(keys.size());
        for (K key : keys) {
            destination.put(key, source.get(key).getValue());
        }
        return destination;
    }

    public Collection<CacheEntry<K, V>> getAll() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        return Collections.unmodifiableCollection(getMap().values());
    }

    @Override
    public boolean containsKey(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        return getMap().containsKey(key);
    }

    @Override
    public Future<V> load(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        CacheLoader<K, ? extends V> cacheLoader = cacheConfiguration.getCacheLoader();
        if (cacheLoader == null) {
            return null;
        }
        if (containsKey(key)) {
            return null;
        }
        CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        FutureTask<V> task = new FutureTask<V>(new CacheLoaderLoadCallable<K, V>(this, cacheLoader, key,
                                                                                 carbonContext.getTenantDomain(),
                                                                                 carbonContext.getTenantId()));
        cacheLoadExecService.submit(task);
        return task;
    }

    private void checkStatusStarted() {
        if (!status.equals(Status.STARTED)) {
            throw new IllegalStateException("The cache status is not STARTED");
        }
    }

    @Override
    public Future<Map<K, ? extends V>> loadAll(final Set<? extends K> keys) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        if (keys == null) {
            throw new NullPointerException("keys");
        }
        CacheLoader<K, ? extends V> cacheLoader = cacheConfiguration.getCacheLoader();
        if (cacheLoader == null) {
            return null;
        }
        if (keys.contains(null)) {
            throw new NullPointerException("key");
        }
        CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        Callable<Map<K, ? extends V>> callable =
                new CacheLoaderLoadAllCallable<K, V>(this, cacheLoader, keys,
                                                     carbonContext.getTenantDomain(),
                                                     carbonContext.getTenantId());
        FutureTask<Map<K, ? extends V>> task = new FutureTask<Map<K, ? extends V>>(callable);
        cacheLoadExecService.submit(task);
        return task;
    }

    @Override
    public CacheStatistics getStatistics() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        return cacheStatistics;
    }

    @Override
    public void put(K key, V value) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.size() >= capacity) {
            EvictionUtil.evict(this, evictionAlgorithm);
        }
        CacheEntry entry = map.get(key);
        V oldValue = entry != null ? (V) entry.getValue() : null;
        if (oldValue == null) {
            map.put(key, new CacheEntry(key, value));
            notifyCacheEntryCreated(key, value);
        } else {
            entry.setValue(value);
            map.put(key, entry);
            notifyCacheEntryUpdated(key, value);
        }
    }

    private void notifyCacheEntryCreated(K key, V value) {
        CacheEntryEvent event = createCacheEntryEvent(key, value);
        for (CacheEntryListener cacheEntryListener : cacheEntryListeners) {
            if (cacheEntryListener instanceof CacheEntryCreatedListener) {
                ((CacheEntryCreatedListener) cacheEntryListener).entryCreated(event);
            }
        }
    }

    private void notifyCacheEntryUpdated(K key, V value) {
        CacheEntryEvent event = createCacheEntryEvent(key, value);
        for (CacheEntryListener cacheEntryListener : cacheEntryListeners) {
            if (cacheEntryListener instanceof CacheEntryUpdatedListener) {
                ((CacheEntryUpdatedListener) cacheEntryListener).entryUpdated(event);
            }
        }
    }

    private void notifyCacheEntryRead(K key, V value) {
        CacheEntryEvent event = createCacheEntryEvent(key, value);
        for (CacheEntryListener cacheEntryListener : cacheEntryListeners) {
            if (cacheEntryListener instanceof CacheEntryReadListener) {
                ((CacheEntryReadListener) cacheEntryListener).entryRead(event);
            }
        }
    }

    private void notifyCacheEntryRemoved(K key, V value) {
        CacheEntryEvent event = createCacheEntryEvent(key, value);
        for (CacheEntryListener cacheEntryListener : cacheEntryListeners) {
            if (cacheEntryListener instanceof CacheEntryRemovedListener) {
                ((CacheEntryRemovedListener) cacheEntryListener).entryRemoved(event);
            }
        }
    }

    private void notifyCacheEntryExpired(K key, V value) {
        CacheEntryEvent event = createCacheEntryEvent(key, value);
        for (CacheEntryListener cacheEntryListener : cacheEntryListeners) {
            if (cacheEntryListener instanceof CacheEntryExpiredListener) {
                ((CacheEntryExpiredListener) cacheEntryListener).entryExpired(event);
            }
        }
    }

    private CacheEntryEvent createCacheEntryEvent(K key, V value) {
        CacheEntryEventImpl event = new CacheEntryEventImpl(this);
        event.setKey(key);
        event.setValue(value);
        return event;
    }

    @Override
    public V getAndPut(K key, V value) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.size() >= capacity) {
            EvictionUtil.evict(this, evictionAlgorithm);
        }
        V oldValue = map.get(key).getValue();
        put(key, value);
        if (oldValue == null) {
            notifyCacheEntryCreated(key, value);
        } else {
            notifyCacheEntryUpdated(key, value);
        }
        return value;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> destination = getMap();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            boolean entryExists = false;
            if (destination.containsKey(key)) {
                entryExists = true;
            }
            V value = entry.getValue();
            destination.put(key, new CacheEntry(key, value));
            if (destination.size() >= capacity) {
                EvictionUtil.evict(this, evictionAlgorithm);
            }
            if (entryExists) {
                notifyCacheEntryUpdated(key, value);
            } else {
                notifyCacheEntryCreated(key, value);
            }
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.size() >= capacity) {
            EvictionUtil.evict(this, evictionAlgorithm);
        }
        if (!map.containsKey(key)) {
            map.put(key, new CacheEntry(key, value));
            notifyCacheEntryCreated(key, value);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        CacheEntry entry = getMap().remove((K) key);
        boolean removed = entry != null;
        if (removed) {
            notifyCacheEntryRemoved((K) key, (V) entry.getValue());
        }
        return removed;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.containsKey(key) && map.get(key).equals(new CacheEntry(key, oldValue))) {
            map.remove(key);
            notifyCacheEntryRemoved(key, oldValue);
            return true;
        }
        return false;
    }

    @Override
    public V getAndRemove(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        CacheEntry entry = getMap().remove(key);
        if (entry != null) {
            V value = (V) entry.getValue();
            notifyCacheEntryRemoved(key, value);
            return value;
        }
        return null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.containsKey(key) && map.get(key).equals(new CacheEntry(key, oldValue))) {
            map.put(key, new CacheEntry(key, newValue));
            notifyCacheEntryUpdated(key, newValue);
            return true;
        }
        return false;
    }

    @Override
    public boolean replace(K key, V value) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.containsKey(key)) {
            map.put(key, new CacheEntry(key, value));
            notifyCacheEntryUpdated(key, value);
            return true;
        }
        return false;
    }

    @Override
    public V getAndReplace(K key, V value) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (map.containsKey(key)) {
            map.put(key, new CacheEntry(key, value));
            notifyCacheEntryUpdated(key, value);
            return value;
        }
        return null;
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        for (K key : keys) {
            CacheEntry entry = map.remove(key);
            notifyCacheEntryRemoved(key, (V) entry.getValue());
        }
    }

    @Override
    public void removeAll() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        Map<K, CacheEntry<K, V>> map = getMap();
        for (Map.Entry<K, CacheEntry<K, V>> entry : map.entrySet()) {
            notifyCacheEntryRemoved(entry.getKey(), entry.getValue().getValue());
        }
        map.clear();
        //TODO: Notify value removed
    }

    @Override
    public CacheConfiguration<K, V> getConfiguration() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        if (cacheConfiguration == null) {
            cacheConfiguration = getDefaultCacheConfiguration();
        }
        return cacheConfiguration;
    }

    private CacheConfiguration<K, V> getDefaultCacheConfiguration() {
        return new CacheConfigurationImpl(true, true, true, true, IsolationLevel.NONE, Mode.NONE,
                                          new CacheConfiguration.Duration[]{new CacheConfiguration.Duration(TimeUnit.MINUTES,
                                                                                                            DEFAULT_CACHE_EXPIRY_MINS),
                                                                            new CacheConfiguration.Duration(TimeUnit.MINUTES,
                                                                                                            DEFAULT_CACHE_EXPIRY_MINS)});
    }

    @Override
    public boolean registerCacheEntryListener(CacheEntryListener<? super K, ? super V> cacheEntryListener) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        return cacheEntryListeners.add(cacheEntryListener);
    }

    @Override
    public boolean unregisterCacheEntryListener(CacheEntryListener<?, ?> cacheEntryListener) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        return cacheEntryListeners.remove(cacheEntryListener);
    }

    @Override
    public Object invokeEntryProcessor(K key, EntryProcessor<K, V> entryProcessor) {
//        V v = getMap().get(key);
        lastAccessed = System.currentTimeMillis();
        return entryProcessor.process(new MutableEntry<K, V>() {
            @Override
            public boolean exists() {
                return false;  //TODO
            }

            @Override
            public void remove() {
                //TODO
            }

            @Override
            public void setValue(V value) {
                //TODO
            }

            @Override
            public K getKey() {
                return null;  //TODO
            }

            @Override
            public V getValue() {
                return null;  //TODO
            }
        });  //TODO change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return this.cacheName;
    }

    @Override
    public CacheManager getCacheManager() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        return cacheManager;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        if (cls.isAssignableFrom(this.getClass())) {
            return cls.cast(this);
        }

        throw new IllegalArgumentException("Unwrapping to " + cls +
                                           " is not a supported by this implementation");
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        return new CacheEntryIterator<K, V>(getMap().values().iterator());
    }

    @Override
    public CacheMXBean getMBean() {
        throw new UnsupportedOperationException("getMBean is an ambiguous method which is not supported");
    }

    @Override
    public void start() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        lastAccessed = System.currentTimeMillis();
        if (status == Status.STARTED) {
            throw new IllegalStateException();
        }
        status = Status.STARTED;
    }

    @Override
    public void stop() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        lastAccessed = System.currentTimeMillis();
        getMap().clear();

        if (!isLocalCache) {
            distributedCache.flush();
        }

        // Unregister the cacheMXBean MBean
        MBeanServer mserver = getMBeanServer();
        try {
            mserver.unregisterMBean(cacheMXBeanObjName);
        } catch (InstanceNotFoundException ignored) {
        } catch (MBeanRegistrationException e) {
            log.error("Cannot unregister CacheMXBean", e);
        }
        status = Status.STOPPED;
        cacheManager.removeCache(cacheName);
    }

    @Override
    public Status getStatus() {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        return status;
    }

    public void expire(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        Map<K, CacheEntry<K, V>> map = getMap();
        CacheEntry entry = map.remove(key);
        if (isIdle()) {
            cacheManager.removeCache(cacheName);
        }
        notifyCacheEntryExpired(key, (V) entry.getValue());
    }

    public void evict(K key) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> map = getMap();
        map.remove(key);
    }

    public void setCacheConfiguration(CacheConfigurationImpl cacheConfiguration) {
        Util.checkAccess(ownerTenantDomain, ownerTenantId);
        this.cacheConfiguration = cacheConfiguration;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public void setEvictionAlgorithm(EvictionAlgorithm evictionAlgorithm) {
        this.evictionAlgorithm = evictionAlgorithm;
    }

    private static final class CacheEntryIterator<K, V> implements Iterator<Entry<K, V>> {
        private Iterator<CacheEntry<K, V>> iterator;

        public CacheEntryIterator(Iterator<CacheEntry<K, V>> iterator) {
            this.iterator = iterator;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Entry<K, V> next() {
            return iterator.next();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            iterator.remove();
        }
    }

    private boolean isIdle() {
        long timeDiff = System.currentTimeMillis() - lastAccessed;
        return getMap().isEmpty() && (timeDiff >= MAX_CACHE_IDLE_TIME_MILLIS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheImpl cache = (CacheImpl) o;

        if (ownerTenantId != cache.ownerTenantId) return false;
        if (cacheManager != null ? !cacheManager.equals(cache.cacheManager) : cache.cacheManager != null)
            return false;
        if (cacheName != null ? !cacheName.equals(cache.cacheName) : cache.cacheName != null)
            return false;
        if (ownerTenantDomain != null ? !ownerTenantDomain.equals(cache.ownerTenantDomain) : cache.ownerTenantDomain != null)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = cacheName != null ? cacheName.hashCode() : 0;
        result = 31 * result + (cacheManager != null ? cacheManager.hashCode() : 0);
        result = 31 * result + (ownerTenantDomain != null ? ownerTenantDomain.hashCode() : 0);
        result = 31 * result + ownerTenantId;
        return result;
    }

    @SuppressWarnings("unchecked")
    void runCacheExpiry() {
        CacheConfiguration cacheConfiguration = getConfiguration();

        CacheConfiguration.Duration modifiedExpiry =
                cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.MODIFIED);
        long modifiedExpiryDuration =
                modifiedExpiry == null ?
                DEFAULT_CACHE_EXPIRY_MILLIS :
                modifiedExpiry.getTimeUnit().toMillis(modifiedExpiry.getDurationAmount());

        CacheConfiguration.Duration accessedExpiry =
                cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.ACCESSED);
        long accessedExpiryDuration =
                accessedExpiry == null ?
                DEFAULT_CACHE_EXPIRY_MILLIS :
                accessedExpiry.getTimeUnit().toMillis(accessedExpiry.getDurationAmount());

        Collection<CacheEntry<K, V>> cacheEntries = getAll();
        for (CacheEntry<K, V> entry : cacheEntries) { // All Cache entries in a Cache
            long lastAccessed = entry.getLastAccessed();
            long lastModified = entry.getLastModified();
            long now = System.currentTimeMillis();

            if (log.isDebugEnabled()) {
                log.debug("Cache:" + cacheName + ", entry:" + entry.getKey() + ", lastAccessed: " +
                          new Date(lastAccessed) + ", lastModified: " + new Date(lastModified));
            }
            if (now - lastAccessed >= accessedExpiryDuration ||
                now - lastModified >= modifiedExpiryDuration) {
                expire(entry.getKey());
                if (log.isDebugEnabled()) {
                    log.debug("Expired: Cache:" + cacheName + ", entry:" + entry.getKey());
                }
            }
        }
    }

    /**
     * Callable used for cache loader.
     *
     * @param <K> the type of the key
     * @param <V> the type of the value
     */
    private static class CacheLoaderLoadCallable<K, V> implements Callable<V> {
        private final CacheImpl<K, V> cache;
        private final CacheLoader<K, ? extends V> cacheLoader;
        private final K key;
        private final String tenantDomain;
        private final int tenantId;

        CacheLoaderLoadCallable(CacheImpl<K, V> cache, CacheLoader<K, ? extends V> cacheLoader, K key,
                                String tenantDomain, int tenantId) {
            this.cache = cache;
            this.cacheLoader = cacheLoader;
            this.key = key;
            this.tenantDomain = tenantDomain;
            this.tenantId = tenantId;
        }

        @Override
        public V call() throws Exception {
            Entry<K, ? extends V> entry;
            try {
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
                carbonContext.setTenantDomain(tenantDomain);
                carbonContext.setTenantId(tenantId);
                entry = cacheLoader.load(key);
                cache.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Could not load cache item with key " + key + " into cache " +
                          cache.getName() + " owned by tenant ", e);
                throw e;
            }
            return entry.getValue();
        }
    }

    /**
     * Callable used for cache loader.
     *
     * @param <K> the type of the key
     * @param <V> the type of the value
     */
    private static class CacheLoaderLoadAllCallable<K, V> implements Callable<Map<K, ? extends V>> {
        private final CacheImpl<K, V> cache;
        private final CacheLoader<K, ? extends V> cacheLoader;
        private final Collection<? extends K> keys;
        private final String tenantDomain;
        private final int tenantId;

        CacheLoaderLoadAllCallable(CacheImpl<K, V> cache,
                                   CacheLoader<K, ? extends V> cacheLoader,
                                   Collection<? extends K> keys,
                                   String tenantDomain, int tenantId) {
            this.cache = cache;
            this.cacheLoader = cacheLoader;
            this.keys = keys;
            this.tenantDomain = tenantDomain;
            this.tenantId = tenantId;
        }

        @Override
        public Map<K, ? extends V> call() throws Exception {
            Map<K, ? extends V> value;
            try {
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
                carbonContext.setTenantDomain(tenantDomain);
                carbonContext.setTenantId(tenantId);
                ArrayList<K> keysNotInStore = new ArrayList<K>();
                for (K key : keys) {
                    if (!cache.containsKey(key)) {
                        keysNotInStore.add(key);
                    }
                }
                value = cacheLoader.loadAll(keysNotInStore);
                cache.putAll(value);
            } catch (Exception e) {
                log.error("Could not load all cache items into cache " + cache.getName() + " owned by tenant ", e);
                throw e;
            }
            return value;
        }
    }
}
