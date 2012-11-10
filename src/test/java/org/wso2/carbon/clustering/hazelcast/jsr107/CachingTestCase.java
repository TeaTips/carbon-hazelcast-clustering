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

import org.testng.annotations.Test;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * TODO: class description
 */
public class CachingTestCase {

    private Cache<String, Integer> cache;
    private String key = "testKey";

    public CachingTestCase() {
        System.out.println("Cache testing....");

        String cacheName = "sampleCache";
        // CacheManager cacheManager = Caching.getCacheManager(); // same as Caching.getCacheManagerFactory().getCacheManager("__default__");

        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        cache = cacheManager.getCache(cacheName);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void checkNonExistentItem() throws Exception {
        assertNull(cache.get(key));
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          dependsOnMethods = "checkNonExistentItem",
          description = "")
    public void checkPut() throws Exception {
        Integer sampleValue = 1245;
        cache.put(key, sampleValue);
        assertEquals(cache.get(key), sampleValue);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void checkMultipleCacheManagers() {
        String cacheName = "sampleCache";
        CacheManager cacheManager1 = Caching.getCacheManagerFactory().getCacheManager("test-1");
        Cache<String, Integer> cache1 = cacheManager1.getCache(cacheName);
        int value1 = 9876;
        cache1.put(key, value1);

        CacheManager cacheManager2 = Caching.getCacheManagerFactory().getCacheManager("test-2");
        Cache<String, String> cache2 = cacheManager2.getCache(cacheName);
        String value2 = "Afkham Azeez";
        cache2.put(key, value2);


        assertEquals(cache1.get(key).intValue(), value1);
        assertEquals(cache2.get(key), value2);

        assertNotEquals(cache1.get(key), value2);
        assertNotEquals(cache2.get(key), value1);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void checkMultipleCaches() {
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test-1");
        Cache<String, Integer> cache1 = cacheManager.getCache("sampleCache1");
        Cache<String, String> cache2 = cacheManager.getCache("sampleCache2");

        int value1 = 9876;
        String value2 = "Afkham Azeez";
        cache1.put(key, value1);
        cache2.put(key, value2);

        assertEquals(cache1.get(key).intValue(), value1);
        assertEquals(cache2.get(key), value2);

        assertNotEquals(cache1.get(key), value2);
        assertNotEquals(cache2.get(key), value1);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void checkWithCustomCacheConfiguration() {
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        String cacheName = "cacheXXX";
        cache = cacheManager.<String, Integer>createCacheBuilder(cacheName).
                setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 10)).
                setStoreByValue(false).build();
        int value = 9876;
        cache.put(key, value);
        assertEquals(cache.get(key).intValue(), value);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          expectedExceptions = {javax.cache.CacheException.class},
          dependsOnMethods = "checkWithCustomCacheConfiguration")
    public void testCreateExistingCache() {
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        String cacheName = "cacheXXX";
        cache = cacheManager.<String, Integer>createCacheBuilder(cacheName).
                setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
                          new CacheConfiguration.Duration(TimeUnit.SECONDS, 10)).
                setStoreByValue(false).build();
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void testSerializableObject() {

        String name = "Afkham Azeez";
        String address = "301/2A, Dehiwela Road";
        Long id = (long) 789;
        SerializableTestObject obj = new SerializableTestObject(name, address, id);

        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        String cacheName = "sampleCache";
        Cache<Long, SerializableTestObject> cache = cacheManager.getCache(cacheName);
        cache.put(id, obj);

        Cache<Long, SerializableTestObject> cache2 = cacheManager.getCache(cacheName);

        assertEquals(cache2.get(id).getId(), id);
        assertEquals(cache2.get(id).getAddress(), address);
        assertEquals(cache2.get(id).getName(), name);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          dependsOnMethods = "testSerializableObject",
          description = "")
    public void testRemoveObjectFromCache() {
        Long id = (long) 789;
        String cacheName = "sampleCache";
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        Cache<Long, SerializableTestObject> cache = cacheManager.getCache(cacheName);
        assertNotNull(cache.get(id));

        Cache<Long, SerializableTestObject> cache2 = cacheManager.getCache(cacheName);
        cache2.remove(id);
        assertNull(cache.get(id));
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void testCacheIterator() {
        Long id = (long) 789;
        String cacheName = "sampleCacheABC";
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        Cache<Long, Long> cache = cacheManager.getCache(cacheName);
        cache.put((long) 123, id);
        cache.put((long) 456, id);
        cache.put((long) 789, id);
        cache.put((long) 12, id);

        int entries = 0;
        for (Cache.Entry<Long, Long> entry : cache) {
            assertNotNull(entry);
            entries ++;
        }
        assertEquals(entries, 4);
    }

    //TODO: tenant tests
}
