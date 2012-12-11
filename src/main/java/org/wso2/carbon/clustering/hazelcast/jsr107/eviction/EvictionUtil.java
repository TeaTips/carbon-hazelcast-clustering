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
package org.wso2.carbon.clustering.hazelcast.jsr107.eviction;

import org.wso2.carbon.clustering.hazelcast.jsr107.CacheImpl;
import org.wso2.carbon.clustering.hazelcast.jsr107.HazelcastCacheManager;

/**
 * TODO: class description
 */
public class EvictionUtil {

    public static void evict(CacheImpl cache, EvictionAlgorithm algorithm) {

        HazelcastCacheManager cacheManager = (HazelcastCacheManager) cache.getCacheManager();
        int ownerTenantId = cacheManager.getOwnerTenantId();
        String cacheManagerName = cacheManager.getName();
        String cacheName = cache.getName();
        synchronized ((ownerTenantId + "." + cacheManagerName + "." + cacheName).intern()) {
            algorithm.evict(cache);
        }
    }
}
