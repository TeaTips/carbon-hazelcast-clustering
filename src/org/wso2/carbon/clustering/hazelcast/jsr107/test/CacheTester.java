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
package org.wso2.carbon.clustering.hazelcast.jsr107.test;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.concurrent.TimeUnit;

public class CacheTester {

    /*public static void main(String[] args) {
        new CacheTest().testCache();
    }*/

    public boolean testCache() {
        System.out.println("Cache testing....");

        String cacheName = "sampleCache";
        // CacheManager cacheManager = Caching.getCacheManager(); // same as Caching.getCacheManagerFactory().getCacheManager("__default__");

        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("test");
        Cache<String, Integer> cache = cacheManager.getCache(cacheName);
        /*cache = cacheManager.<String, Integer>createCacheBuilder(cacheName).setExpiry(CacheConfiguration.ExpiryType.MODIFIED,
                                                                                      new CacheConfiguration.Duration(TimeUnit.SECONDS, 10)).setStoreByValue(false).build();*/

        String key = "key";

        Integer value1 = cache.get(key);
        if(value1 == null){
            value1 = 0;
        } else {
            value1 ++;
        }
        System.out.println("value1 = " + value1);
        cache.put(key, value1);
        Integer value2 = cache.get(key);
        System.out.println("value2 = " + value2);
//        assertEquals(value1, value2);


//        cache.remove("key");

//        assertNull(cache.get("key"));
        return false;
    }

}
