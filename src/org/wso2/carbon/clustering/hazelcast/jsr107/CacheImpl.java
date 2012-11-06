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
import org.wso2.carbon.clustering.hazelcast.HazelcastInstanceManager;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.CacheStatistics;
import javax.cache.Status;
import javax.cache.event.CacheEntryListener;
import javax.cache.mbeans.CacheMXBean;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * TODO: class description
 */
public class CacheImpl<K, V> implements Cache<K, V> {

    private String cacheName;
    private HazelcastInstance hazelcastInstance =
            HazelcastInstanceManager.getInstance().getHazelcastInstance();

    public CacheImpl(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public V get(K key) {
        IMap<Object, Object> map = hazelcastInstance.getMap("$cache." + cacheName);
        return (V) map.get(key);
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean containsKey(K key) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Future<V> load(K key) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Future<Map<K, ? extends V>> loadAll(Set<? extends K> keys) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheStatistics getStatistics() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void put(K key, V value) {
        //To change body of implemented methods use File | Settings | File Templates.

        //TODO: Get Hazelcast instance, get a map and put
        // Maintain a Hazelcast map per cache
        // TODO: Add the tenant information
        IMap<Object, Object> map = hazelcastInstance.getMap("$cache." + cacheName);
        map.put(key, value);
    }

    @Override
    public V getAndPut(K key, V value) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean remove(K key) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean remove(K key, V oldValue) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public V getAndRemove(K key) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean replace(K key, V value) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public V getAndReplace(K key, V value) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void removeAll() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheConfiguration<K, V> getConfiguration() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean registerCacheEntryListener(CacheEntryListener<? super K, ? super V> cacheEntryListener) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean unregisterCacheEntryListener(CacheEntryListener<?, ?> cacheEntryListener) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Object invokeEntryProcessor(K key, EntryProcessor<K, V> entryProcessor) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheManager getCacheManager() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Iterator<Entry<K, V>> iterator() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheMXBean getMBean() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void start() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void stop() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Status getStatus() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
