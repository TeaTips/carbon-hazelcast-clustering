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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * TODO: class description
 *
 * TODO: Also handle cache eviction - remove items from cache when the cache is full
 */
public class CacheExpiryTask implements Runnable {
    private static final Log log = LogFactory.getLog(CacheExpiryTask.class);

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        log.info("Cache expiry scheduler running...");

        // Get all the caches
        // Get the configurations from the caches
        // Check the timeout policy and clear out old values
        try {
            Map<String,CacheManager> cacheManagers =
                    ((CacheManagerFactoryImpl) Caching.getCacheManagerFactory()).getCacheManagers();
            for (CacheManager cacheManager : cacheManagers.values()) {  // All CacheManagers
                Iterable<Cache<?,?>> caches = cacheManager.getCaches();
                for (Cache<?, ?> cache : caches) { // All Caches in a CacheManager
                    CacheConfiguration cacheConfiguration = cache.getConfiguration();

                    CacheConfiguration.Duration modifiedExpiry =
                            cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.MODIFIED);
                    long modifiedExpiryDuration =
                            modifiedExpiry.getTimeUnit().toMillis(modifiedExpiry.getDurationAmount());

                    CacheConfiguration.Duration accessedExpiry =
                            cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.ACCESSED);
                    long accessedExpiryDuration =
                            accessedExpiry.getTimeUnit().toMillis(accessedExpiry.getDurationAmount());

                    Collection<CacheEntry> cacheEntries = ((CacheImpl) cache).getAll();
                    for (CacheEntry entry : cacheEntries) { // All Cache entries in a Cache
                        long lastAccessed = entry.getLastAccessed();
                        long lastModified = entry.getLastModified();
                        long now = System.currentTimeMillis();

                        if (log.isDebugEnabled()) {
                            log.debug("Cache:" + cache.getName() + ", entry:" + entry.getKey() + ", lastAccessed: " +
                                      new Date(lastAccessed) + ", lastModified: " + new Date(lastModified));
                        }
                        if(now - lastAccessed >= accessedExpiryDuration ||
                           now - lastModified >= modifiedExpiryDuration){
                            ((CacheImpl)cache).remove(entry.getKey());
                            log.info("Expired: Cache:" + cache.getName() + ", entry:" + entry.getKey());
                        }
                    }
                    if(((CacheImpl)cache).isEmpty()){
                        cache.stop();
                        cacheManager.removeCache(cache.getName());
                    }
                }
                if(((HazelcastCacheManager)cacheManager).isEmpty()){
                    cacheManager.shutdown();
                    cacheManagers.remove(cacheManager.getName());
                }
            }
        } catch (Throwable t) {
            log.error("Error occurred while running cache expiry task", t);
        }
    }
}
