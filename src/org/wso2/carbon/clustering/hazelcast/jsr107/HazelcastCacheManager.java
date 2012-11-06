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
import javax.cache.CacheBuilder;
import javax.cache.CacheManager;
import javax.cache.OptionalFeature;
import javax.cache.Status;

/**
 * TODO: class description
 */
public class HazelcastCacheManager implements CacheManager {
    @Override
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Status getStatus() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <K, V> CacheBuilder<K, V> createCacheBuilder(String cacheName) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <K, V> Cache<K, V> getCache(String cacheName) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Iterable<Cache<?, ?>> getCaches() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean removeCache(String cacheName) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public javax.transaction.UserTransaction getUserTransaction() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isSupported(OptionalFeature optionalFeature) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void shutdown() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
