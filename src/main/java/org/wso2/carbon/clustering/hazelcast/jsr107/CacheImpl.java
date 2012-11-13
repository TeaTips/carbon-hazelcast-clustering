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

import static org.wso2.carbon.clustering.hazelcast.jsr107.Util.checkAccess;

/**
 * TODO: class description
 * <p/>
 * TODO: Cache statistics
 */
@SuppressWarnings("unchecked")
public class CacheImpl<K, V> implements Cache<K, V> {
    private static final Log log = LogFactory.getLog(CacheImpl.class);
    private static final int CACHE_LOADER_THREADS = 2;

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
    private CacheMXBeanImpl cacheMXBean;
    private final ExecutorService cacheLoadExecService = Executors.newFixedThreadPool(CACHE_LOADER_THREADS);

    private String ownerTenantDomain;
    private int ownerTenantId;

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
            log.info("Using Hazelcast based distributed cache");
            distributedCache = hazelcastInstance.getMap("$cache.$domain[" + ownerTenantDomain + "]" +
                                                        cacheManager.getName() + "#" + cacheName);
        } else {
            log.info("Using local cache");
            isLocalCache = true;
            localCache = new ConcurrentHashMap<K, CacheEntry<K, V>>();
        }
        cacheStatistics = new CacheStatisticsImpl();
        this.cacheMXBean = new CacheMXBeanImpl(this);
        registerMBean(cacheMXBean, ownerTenantDomain);
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

    private void registerMBean(Object mbeanInstance, String tenantDomain) {
        String serverPackage = "org.wso2.carbon";
        try {
            String objectName = serverPackage + ":type=Cache,tenant=" + tenantDomain +
                                ",manager=" + cacheManager.getName() + ",name=" + cacheName;
            MBeanServer mserver = getMBeanServer();
            Set set = mserver.queryNames(new ObjectName(objectName), null);
            if (set.isEmpty()) {
                cacheMXBeanObjName = new ObjectName(objectName);
                mserver.registerMBean(mbeanInstance, cacheMXBeanObjName);
            } else {
                log.debug("MBean " + objectName + " already exists");
                throw new Exception("MBean " + objectName + " already exists");
            }
        } catch (Exception e) {
            String msg = "Could not register " + mbeanInstance.getClass() + " MBean";
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> source = getMap();
        Map<K, V> destination = new HashMap<K, V>(keys.size());
        for (K key : keys) {
            destination.put(key, (V) source.get(key).getValue());
        }
        return destination;
    }

    public Collection<CacheEntry<K, V>> getAll() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        return Collections.unmodifiableCollection(getMap().values());
    }

    @Override
    public boolean containsKey(K key) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        return getMap().containsKey(key);
    }

    @Override
    public Future<V> load(K key) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        return cacheStatistics;
    }

    @Override
    public void put(K key, V value) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> map = getMap();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        V oldValue = (V) getMap().get(key).getValue();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> destination = getMap();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            boolean entryExists = false;
            if (destination.containsKey(key)) {
                entryExists = true;
            }
            V value = entry.getValue();
            destination.put(key, new CacheEntry(key, value));
            if (entryExists) {
                notifyCacheEntryUpdated(key, value);
            } else {
                notifyCacheEntryCreated(key, value);
            }
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> map = getMap();
        if (!map.containsKey(key)) {
            map.put(key, new CacheEntry(key, value));
            notifyCacheEntryCreated(key, value);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object key) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        CacheEntry entry = getMap().remove((K) key);
        boolean removed = entry != null;
        if (removed) {
            notifyCacheEntryRemoved((K) key, (V) entry.getValue());
        }
        return removed;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
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
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> map = getMap();
        for (K key : keys) {
            CacheEntry entry = map.remove(key);
            notifyCacheEntryRemoved(key, (V) entry.getValue());
        }
    }

    @Override
    public void removeAll() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();

        Map<K, CacheEntry<K, V>> map = getMap();
        for (Map.Entry<K, CacheEntry<K, V>> entry : map.entrySet()) {
            notifyCacheEntryRemoved(entry.getKey(), (V) entry.getValue().getValue());
        }
        map.clear();
        //TODO: Notify value removed
    }

    @Override
    public CacheConfiguration<K, V> getConfiguration() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        if (cacheConfiguration == null) {
            cacheConfiguration = getDefaultCacheConfiguration();
        }
        return cacheConfiguration;
    }

    private CacheConfiguration<K, V> getDefaultCacheConfiguration() {
        return new CacheConfigurationImpl(true, true, true, true, IsolationLevel.NONE, Mode.NONE,
                                          new CacheConfiguration.Duration[]{new CacheConfiguration.Duration(TimeUnit.MINUTES, 15),
                                                                            new CacheConfiguration.Duration(TimeUnit.MINUTES, 15)});
    }

    @Override
    public boolean registerCacheEntryListener(CacheEntryListener<? super K, ? super V> cacheEntryListener) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return cacheEntryListeners.add(cacheEntryListener);
    }

    @Override
    public boolean unregisterCacheEntryListener(CacheEntryListener<?, ?> cacheEntryListener) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return cacheEntryListeners.remove(cacheEntryListener);
    }

    @Override
    public Object invokeEntryProcessor(K key, EntryProcessor<K, V> entryProcessor) {
//        V v = getMap().get(key);
        return entryProcessor.process(new MutableEntry<K, V>() {
            @Override
            public boolean exists() {
                return false;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void remove() {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void setValue(V value) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public K getKey() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public V getValue() {
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });  //TODO change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return this.cacheName;
    }

    @Override
    public CacheManager getCacheManager() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return cacheManager;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        if (cls.isAssignableFrom(this.getClass())) {
            return cls.cast(this);
        }

        throw new IllegalArgumentException("Unwrapping to " + cls +
                                           " is not a supported by this implementation");
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return new CacheEntryIterator<K, V>(getMap().values().iterator());
    }

    @Override
    public CacheMXBean getMBean() {
        return cacheMXBean;
    }

    @Override
    public void start() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        if (status == Status.STARTED) {
            throw new IllegalStateException();
        }
        status = Status.STARTED;
    }

    @Override
    public void stop() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        getMap().clear();

        if (!isLocalCache) {
            distributedCache.flush();
        }

        // Unregister the cacheMXBean MBean
        MBeanServer mserver = getMBeanServer();
        try {
            mserver.unregisterMBean(cacheMXBeanObjName);
        } catch (InstanceNotFoundException e) {
            log.error("Cannot unregister CacheMXBean", e);
        } catch (MBeanRegistrationException e) {
            log.error("Cannot unregister CacheMXBean", e);
        }
        status = Status.STOPPED;
    }

    @Override
    public Status getStatus() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return status;
    }

    public boolean isEmpty() {
        checkAccess(ownerTenantDomain, ownerTenantId);
        return getMap().isEmpty();
    }

    public void expire(K key) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        CacheEntry entry = getMap().remove(key);
        notifyCacheEntryExpired(key, (V) entry.getValue());
    }

    public void evict(K key) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        checkStatusStarted();
        Map<K, CacheEntry<K, V>> map = getMap();
        map.remove(key);
    }

    public void setCacheConfiguration(CacheConfigurationImpl cacheConfiguration) {
        checkAccess(ownerTenantDomain, ownerTenantId);
        this.cacheConfiguration = cacheConfiguration;
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
            Entry<K, ? extends V> entry = null;
            try {
                PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
                carbonContext.setTenantDomain(tenantDomain);
                carbonContext.setTenantId(tenantId);
                entry = cacheLoader.load(key);
                cache.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Could not load cache item with key " + key + " into cache " + cache.getName() + " owned by tenant ", e);
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
