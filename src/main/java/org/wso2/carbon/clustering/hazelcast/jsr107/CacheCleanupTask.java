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
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * TODO: class description
 * <p/>
 * TODO: Also handle cache eviction - remove items from cache when the cache is full
 */
public class CacheCleanupTask implements Runnable {
    private static final Log log = LogFactory.getLog(CacheCleanupTask.class);

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void run() {
        log.info("Cache expiry scheduler running...");

        // Get all the caches
        // Get the configurations from the caches
        // Check the timeout policy and clear out old values
        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            carbonContext.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            carbonContext.setTenantId(MultitenantConstants.SUPER_TENANT_ID);

            Map<String, Map<String, CacheManager>> globalCacheManagerMap =
                    ((CacheManagerFactoryImpl) Caching.getCacheManagerFactory()).getGlobalCacheManagerMap();
            for (Map.Entry<String, Map<String, CacheManager>> cacheManagerEntry : globalCacheManagerMap.entrySet()) {  // All CacheManagers of all tenants
                String tenantDomain = cacheManagerEntry.getKey();
                Map<String, CacheManager> tenantCacheManagers = cacheManagerEntry.getValue();
                for (Map.Entry<String, CacheManager> entry : tenantCacheManagers.entrySet()) {  // All CacheManagers of a tenant
                    String cacheManagerName = entry.getKey();
                    CacheManager cacheManager = entry.getValue();
                    boolean isCacheManagerShutdown = cleanup(cacheManager);
                    if (isCacheManagerShutdown) {
                        tenantCacheManagers.remove(cacheManagerName);
                    }
                }
                if (tenantCacheManagers.isEmpty()) {
                    globalCacheManagerMap.remove(tenantDomain);
                }
            }
        } catch (Throwable t) {
            log.error("Error occurred while running cache expiry task", t);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    /**
     * @param cacheManager The CacheManager to be cleaned up
     * @return true if the cacheManager was shutdown
     */
    @SuppressWarnings("unchecked")
    private boolean cleanup(CacheManager cacheManager) {
        Iterable<Cache<?, ?>> caches = cacheManager.getCaches();
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

            CacheImpl cacheImpl = (CacheImpl) cache;
            Collection<CacheEntry> cacheEntries = cacheImpl.getAll();
            for (CacheEntry entry : cacheEntries) { // All Cache entries in a Cache
                long lastAccessed = entry.getLastAccessed();
                long lastModified = entry.getLastModified();
                long now = System.currentTimeMillis();

                if (log.isDebugEnabled()) {
                    log.debug("Cache:" + cache.getName() + ", entry:" + entry.getKey() + ", lastAccessed: " +
                              new Date(lastAccessed) + ", lastModified: " + new Date(lastModified));
                }
                if (now - lastAccessed >= accessedExpiryDuration ||
                    now - lastModified >= modifiedExpiryDuration) {
                    cacheImpl.expire(entry.getKey());
                    log.info("Expired: Cache:" + cache.getName() + ", entry:" + entry.getKey());
                }
            }
            if (cacheImpl.isEmpty()) { // If a cache is empty, remove it
                cacheManager.removeCache(cache.getName());
            }
        }
        if (((HazelcastCacheManager) cacheManager).isEmpty()) { // If cacheManager does not have any cache, shut it down
            cacheManager.shutdown();
            return true;
        }
        return false;
    }
}
