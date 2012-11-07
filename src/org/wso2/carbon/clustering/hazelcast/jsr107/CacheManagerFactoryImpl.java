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

import javax.cache.CacheManager;
import javax.cache.CacheManagerFactory;
import javax.cache.CachingShutdownException;
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: class description
 */
public class CacheManagerFactoryImpl implements CacheManagerFactory {
    private Map<String, CacheManager>  cacheManagers = new HashMap<String, CacheManager>();

    @Override
    public CacheManager getCacheManager(String name) {
        CacheManager cacheManager = cacheManagers.get(name);
        if(cacheManager == null){
            cacheManager = new HazelcastCacheManager(name);
            cacheManagers.put(name, cacheManager);
        }
        return cacheManager;
    }

    @Override
    public CacheManager getCacheManager(ClassLoader classLoader, String name) {
        // TODO: Take classloader into consideration
        return getCacheManager(name);
    }

    @Override
    public void close() throws CachingShutdownException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean close(ClassLoader classLoader) throws CachingShutdownException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean close(ClassLoader classLoader, String name) throws CachingShutdownException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
