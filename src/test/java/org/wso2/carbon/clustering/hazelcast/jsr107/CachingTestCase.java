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

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;

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
        Assert.assertNull(cache.get(key));
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107"},
          description = "")
    public void checkPut() throws Exception {
        Integer sampleValue = 1245;
        cache.put(key, sampleValue);
        Assert.assertEquals(cache.get(key), sampleValue);
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


        Assert.assertEquals((int) cache1.get(key), value1);
        Assert.assertEquals(cache2.get(key), value2);

        Assert.assertNotEquals(cache1.get(key), value2);
        Assert.assertNotEquals(cache2.get(key), value1);
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

        Assert.assertEquals((int) cache1.get(key), value1);
        Assert.assertEquals(cache2.get(key), value2);

        Assert.assertNotEquals(cache1.get(key), value2);
        Assert.assertNotEquals(cache2.get(key), value1);
    }
}
