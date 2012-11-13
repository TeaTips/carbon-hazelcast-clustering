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
import javax.cache.CacheManager;
import javax.cache.Caching;

public class CacheTester {
//    private Cache<String, Integer> cache;

    public CacheTester() {
        System.out.println("Cache testing....");

        String cacheName = "sampleCache";
        // CacheManager cacheManager = Caching.getCacheManager(); // same as Caching.getCacheManagerFactory().getCacheManager("__default__");

//        cache = cacheManager.getCache(cacheName);
    }

    /*public static void main(String[] args) {
        new CacheTest().testCache();
    }*/

    public void put(String manager, String cacheName, String key, int value){

//        Integer value1 = cache.get(key);
//        System.out.println("value1 = " + value1);
//        if(value1 == null){
//            value1 = 0;
//        } else {
//            value1 ++;
//        }
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(manager);
        Cache<String, Integer> cache = cacheManager.getCache(cacheName);
        cache.put(key, value);
    }

    public int get(String manager, String cacheName, String key){
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(manager);
        Cache<String, Integer> cache = cacheManager.getCache(cacheName);
        Integer integer = cache.get(key);
        return integer == null ? 0 : integer;
    }

    public void print(String manager, String cacheName){
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(manager);
        Cache<String, Integer> cache = cacheManager.getCache(cacheName);
        for (Cache.Entry<String, Integer> entry : cache) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }


}
