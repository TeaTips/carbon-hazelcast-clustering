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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.CacheManagerFactory;
import javax.cache.CachingShutdownException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TODO: class description
 */
public class CacheManagerFactoryImpl implements CacheManagerFactory {

    private static CacheCleanupTask cacheCleanupTask = new CacheCleanupTask();

    static{
        ScheduledExecutorService cacheExpiryScheduler = Executors.newScheduledThreadPool(10);
        cacheExpiryScheduler.scheduleWithFixedDelay(cacheCleanupTask, 0, 30, TimeUnit.SECONDS);
    }

    static void addCacheForMonitoring(Cache cache){
        cacheCleanupTask.addCacheForMonitoring(cache);
    }

    /**
     * Map<tenantDomain, Map<cacheManagerName,CacheManager> >
     */
    private Map<String, Map<String, CacheManager>> globalCacheManagerMap =
            new ConcurrentHashMap<String, Map<String, CacheManager>>();

    @Override
    public CacheManager getCacheManager(String cacheManagerName) {
        String tenantDomain = Util.getTenantDomain();
        CacheManager cacheManager;
        synchronized ((tenantDomain + "_$_#" + cacheManagerName).intern()) {
            Map<String, CacheManager> cacheManagers = globalCacheManagerMap.get(tenantDomain);
            if(cacheManagers == null){
                cacheManagers = new ConcurrentHashMap<String, CacheManager>();
                globalCacheManagerMap.put(tenantDomain, cacheManagers);
                cacheManager = new HazelcastCacheManager(cacheManagerName, this);
                cacheManagers.put(cacheManagerName, cacheManager);
            } else {
                cacheManager = cacheManagers.get(cacheManagerName);
                if (cacheManager == null) {
                    cacheManager = new HazelcastCacheManager(cacheManagerName, this);
                    cacheManagers.put(cacheManagerName, cacheManager);
                }
            }
        }
        return cacheManager;
    }

    @Override
    public CacheManager getCacheManager(ClassLoader classLoader, String name) {
        // Since we have a single CacheManager, we don't have to take the ClassLoader into consideration
        return getCacheManager(name);
    }

    @Override
    public void close() throws CachingShutdownException {
        String tenantDomain = Util.getTenantDomain();
        Map<String, CacheManager> cacheManagers = globalCacheManagerMap.get(tenantDomain);
        if(cacheManagers != null){
            for (CacheManager cacheManager : cacheManagers.values()) {
                cacheManager.shutdown();
            }
            cacheManagers.clear();
        }
    }

    @Override
    public boolean close(ClassLoader classLoader) throws CachingShutdownException {
        close();
        return true;
    }

    @Override
    public boolean close(ClassLoader classLoader, String name) throws CachingShutdownException {
        String tenantDomain = Util.getTenantDomain();
        Map<String, CacheManager> cacheManagers = globalCacheManagerMap.get(tenantDomain);
        CacheManager cacheManager;
        if (cacheManagers != null) {
            cacheManager = cacheManagers.get(name);
            cacheManager.shutdown();
            return true;
        }
        return false;
    }

    public void removeCacheManager(HazelcastCacheManager cacheManager, String tenantDomain) {
        Map<String, CacheManager> cacheManagers = globalCacheManagerMap.get(tenantDomain);
        if(cacheManagers != null){
            cacheManagers.remove(cacheManager.getName());
        }
    }
}
