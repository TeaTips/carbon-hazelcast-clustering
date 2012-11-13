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
import org.wso2.carbon.context.PrivilegedCarbonContext;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * TODO: class description
 */
public class MultitenantCachingTestCase {
    private Cache<String, Integer> cache;

    public MultitenantCachingTestCase() {
        System.setProperty("carbon.home", new File(".").getAbsolutePath());

        String cacheName = "sampleCache";
        // CacheManager cacheManager = Caching.getCacheManager(); // same as Caching.getCacheManagerFactory().getCacheManager("__default__");

        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("foo.com");
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(1);

        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        cache = cacheManager.getCache(cacheName);
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107.mt"},
          expectedExceptions = {SecurityException.class},
          description = "")
    public void testIllegalAccess() {
        Integer sampleValue = 1245;
        String key1 = "testIllegalAccess-123";
        cache.put(key1, sampleValue);

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("bar.com");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(2);

            cache.get(key1); // Should throw SecurityException
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107.mt"},
          description = "")
    public void testLegalAccess() {
        Integer sampleValue = 1245;
        String key1 = "testLegalAccess-123";
        cache.put(key1, sampleValue);

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("foo.com");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(1);

            assertEquals(cache.get(key1), sampleValue);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    @Test(groups = {"org.wso2.carbon.clustering.hazelcast.jsr107.mt"},
          description = "")
    public void testCreateCacheWithSameNameByTwoTenants() {
        Integer sampleValue = 1245;
        String key1 = "testCreateCacheWithSameNameByTwoTenants-123";
        String key2 = "testCreateCacheWithSameNameByTwoTenants-1234";
        String cacheManagerName = "testCacheManager";
        String cacheName = "sampleCache";

        // Tenant wso2.com
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("wso2.com");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(1);

            CacheManager cacheManager =
                    Caching.getCacheManagerFactory().getCacheManager(cacheManagerName);
            Cache<String, Integer> cache1 = cacheManager.getCache(cacheName);
            cache1.put(key1, sampleValue);
            cache1.put(key2, sampleValue);
            cache1 = cacheManager.getCache(cacheName);
            assertEquals(sampleValue, cache1.get(key1));
            checkCacheSize(cache1, 2);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

        // Tenant wso2.com
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain("ibm.com");
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(2);

            CacheManager cacheManager =
                    Caching.getCacheManagerFactory().getCacheManager(cacheManagerName);
            Cache<String, Integer> cache1 = cacheManager.getCache(cacheName);
            cache1.put(key1, sampleValue);
            cache1 = cacheManager.getCache(cacheName);
            assertEquals(sampleValue, cache1.get(key1));

            checkCacheSize(cache1, 1);
            cache1 = cacheManager.getCache(cacheName);
            cache1.remove(key1);
            cache1 = cacheManager.getCache(cacheName);
            checkCacheSize(cache1, 0);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }

    }

    private void checkCacheSize(Cache<String, Integer> cache1, int expectedCacheSize) {
        int cacheSize = 0;
        for (Cache.Entry<String, Integer> entry : cache1) {
            cacheSize++;
        }
        assertEquals(cacheSize, expectedCacheSize);
    }
}
