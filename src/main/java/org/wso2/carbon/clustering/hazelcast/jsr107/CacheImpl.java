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

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * TODO: class description
 * <p/>
 * TODO: Cache statistics
 */
@SuppressWarnings("unchecked")
public class CacheImpl<K, V> implements Cache<K, V> {
    private static final Log log = LogFactory.getLog(CacheImpl.class);
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

    public CacheImpl(String tenantDomain, String cacheName, CacheManager cacheManager) {
        this("$cache." + tenantDomain + "#" + cacheName, cacheManager);
        this.cacheName = cacheName;
        log.info("Created cache " + cacheName + " for tenant " + tenantDomain);
    }

    public CacheImpl(String cacheName, CacheManager cacheManager) {
        this.cacheName = cacheName;
        this.cacheManager = cacheManager;
        HazelcastInstance hazelcastInstance =
                HazelcastInstanceManager.getInstance().getHazelcastInstance();
        if (hazelcastInstance != null) {
            log.info("Using Hazelcast based distributed cache");
            distributedCache = hazelcastInstance.getMap("$cache." + cacheManager.getName() + "#" + cacheName);  //TODO: IMPORTANT: Get the tenant ID in
        } else {
            log.info("Using local cache");
            isLocalCache = true;
            localCache = new ConcurrentHashMap<K, CacheEntry<K, V>>();
        }
        cacheStatistics = new CacheStatisticsImpl();
        this.cacheMXBean = new CacheMXBeanImpl(this);
        registerMBean(cacheMXBean);
        status = Status.STARTED;
    }

    private void registerMBean(Object mbeanInstance,
                               String objectName) throws Exception {

        MBeanServer mserver = getMBeanServer();
        Set set = mserver.queryNames(new ObjectName(objectName), null);
        if (set.isEmpty()) {
            cacheMXBeanObjName = new ObjectName(objectName);
            mserver.registerMBean(mbeanInstance, cacheMXBeanObjName);
        } else {
            log.debug("MBean " + objectName + " already exists");
            throw new Exception("MBean " + objectName + " already exists");
        }
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

    private void registerMBean(Object mbeanInstance) {
        String serverPackage = "org.wso2.carbon";
        try {
            //TODO: Group the MBeans by tenant
            registerMBean(mbeanInstance, serverPackage + ":type=Cache,manager=" +
                                         cacheManager.getName() + ",name=" + cacheName);
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Map<K, CacheEntry<K, V>> source = getMap();
        Map<K, V> destination = new HashMap<K, V>(keys.size());
        for (K key : keys) {
            destination.put(key, (V) source.get(key).getValue());
        }
        return destination;
    }

    public Collection<CacheEntry<K, V>> getAll() {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        return Collections.unmodifiableCollection(getMap().values());
    }

    @Override
    public boolean containsKey(K key) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        return getMap().containsKey(key);
    }

    @Override
    public Future<V> load(K key) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Future<Map<K, ? extends V>> loadAll(Set<? extends K> keys) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheStatistics getStatistics() {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        return cacheStatistics;
    }

    @Override
    public void put(K key, V value) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Map<K, CacheEntry<K, V>> destination = getMap();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            destination.put(key, new CacheEntry(key, entry.getValue()));
            //TODO: Notify CacheListeners
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        CacheEntry entry = getMap().remove((K) key);
        boolean removed = entry != null;
        if (removed) {
            notifyCacheEntryRemoved((K) key, (V) entry.getValue());
        }
        return removed;
    }

    @Override
    public boolean remove(K key, V oldValue) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Map<K, CacheEntry<K, V>> map = getMap();
        for (K key : keys) {
            CacheEntry entry = map.remove(key);
            notifyCacheEntryRemoved(key, (V) entry.getValue());
        }
    }

    @Override
    public void removeAll() {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }

        Map<K, CacheEntry<K, V>> map = getMap();
        for (Map.Entry<K, CacheEntry<K, V>> entry : map.entrySet()) {
            notifyCacheEntryRemoved(entry.getKey(), (V) entry.getValue().getValue());
        }
        map.clear();
        //TODO: Notify value removed
    }

    @Override
    public CacheConfiguration<K, V> getConfiguration() {
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
        return cacheEntryListeners.add(cacheEntryListener);
    }

    @Override
    public boolean unregisterCacheEntryListener(CacheEntryListener<?, ?> cacheEntryListener) {
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
        return this.cacheName;
    }

    @Override
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return new CacheEntryIterator<K, V>(getMap().values().iterator());
    }

    @Override
    public CacheMXBean getMBean() {
        return cacheMXBean;
    }

    @Override
    public void start() {
        if (status == Status.STARTED) {
            throw new IllegalStateException();
        }
        status = Status.STARTED;
    }

    @Override
    public void stop() {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
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
        return status;
    }

    public boolean isEmpty() {
        return getMap().isEmpty();
    }

    public void expire(K key) {
        CacheEntry entry = getMap().remove(key);
        notifyCacheEntryExpired(key, (V) entry.getValue());
    }

    public void evict(K key) {
        if (status != Status.STARTED) {
            throw new IllegalStateException();
        }
        Map<K, CacheEntry<K, V>> map = getMap();
        map.remove(key);
    }

    public void setCacheConfiguration(CacheConfigurationImpl cacheConfiguration) {
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
}
