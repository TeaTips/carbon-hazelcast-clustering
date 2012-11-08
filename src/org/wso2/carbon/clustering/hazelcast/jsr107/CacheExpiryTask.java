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
import java.util.Iterator;
import java.util.Map;

/**
 * TODO: class description
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
            for (CacheManager cacheManager : cacheManagers.values()) {
                Iterable<Cache<?,?>> caches = cacheManager.getCaches();
                for (Cache<?, ?> cache : caches) {
                    CacheConfiguration cacheConfiguration = cache.getConfiguration();
                    CacheConfiguration.Duration modifiedExpiryTime =
                            cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.MODIFIED);
                    CacheConfiguration.Duration accessedExpiryTime =
                            cacheConfiguration.getExpiry(CacheConfiguration.ExpiryType.ACCESSED);
                    Collection<CacheEntry> cacheEntries = ((CacheImpl) cache).getAll();
                    for (CacheEntry entry : cacheEntries) {
                        long lastAccessed = entry.getLastAccessed();
                        long lastModified = entry.getLastModified();

                        //TODO: Impl
                        log.info("Cache:" + cache.getName() + ", entry:" + entry.getKey() + ", lastAccessed: " +
                                 new Date(lastAccessed) + ", lastModified: " + new Date(lastModified) );
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Error occurred while running cache expiry task", t);
        }
    }
}