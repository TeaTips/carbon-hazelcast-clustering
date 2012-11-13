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

import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.cache.CacheManager;
import javax.cache.CacheManagerFactory;
import javax.cache.CachingShutdownException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TODO: class description
 */
public class CacheManagerFactoryImpl implements CacheManagerFactory {

    static{
        ScheduledExecutorService cacheExpiryScheduler = Executors.newScheduledThreadPool(10);
        cacheExpiryScheduler.scheduleWithFixedDelay(new CacheCleanupTask(), 0, 30, TimeUnit.SECONDS);
    }

    /**
     * Map<tenantDomain, Map<cacheManagerName,CacheManager> >
     */
    private Map<String, Map<String, CacheManager>> globalCacheManagerMap =
            new ConcurrentHashMap<String, Map<String, CacheManager>>();

    public Map<String, Map<String, CacheManager>> getGlobalCacheManagerMap(){
        Util.checkAccess(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME,
                         MultitenantConstants.SUPER_TENANT_ID);
        return Collections.unmodifiableMap(globalCacheManagerMap);
    }

    @Override
    public CacheManager getCacheManager(String name) {
        String tenantDomain = Util.getTenantDomain();
        CacheManager cacheManager;
        Map<String, CacheManager> cacheManagers = globalCacheManagerMap.get(tenantDomain);
        if(cacheManagers == null){
            cacheManagers = new ConcurrentHashMap<String, CacheManager>();
            globalCacheManagerMap.put(tenantDomain, cacheManagers);
            cacheManager = new HazelcastCacheManager(name);
            cacheManagers.put(name, cacheManager);
        } else {
            cacheManager = cacheManagers.get(name);
            if (cacheManager == null) {
                cacheManager = new HazelcastCacheManager(name);
                cacheManagers.put(name, cacheManager);
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
        CacheManager cacheManager = null;
        if (cacheManagers != null) {
            cacheManager = cacheManagers.get(name);
            cacheManager.shutdown();
            return true;
        }
        return false;
    }

    public void removeCacheManager(String tenantDomain) {
        Util.checkAccess(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME,
                         MultitenantConstants.SUPER_TENANT_ID);
        globalCacheManagerMap.remove(tenantDomain);
    }
}
