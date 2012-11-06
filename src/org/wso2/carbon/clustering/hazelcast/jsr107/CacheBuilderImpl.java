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
import javax.cache.CacheConfiguration;
import javax.cache.CacheLoader;
import javax.cache.CacheWriter;
import javax.cache.event.CacheEntryListener;
import javax.cache.transaction.IsolationLevel;
import javax.cache.transaction.Mode;

/**
 * TODO: class description
 */
public class CacheBuilderImpl<K,V> implements CacheBuilder<K,V>{
    @Override
    public Cache<K, V> build() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setCacheLoader(CacheLoader<K, ? extends V> cacheLoader) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setCacheWriter(CacheWriter<? super K, ? super V> cacheWriter) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> registerCacheEntryListener(CacheEntryListener<K, V> cacheEntryListener) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setStoreByValue(boolean storeByValue) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setTransactionEnabled(IsolationLevel isolationLevel, Mode mode) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setStatisticsEnabled(boolean enableStatistics) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setReadThrough(boolean readThrough) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setWriteThrough(boolean writeThrough) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public CacheBuilder<K, V> setExpiry(CacheConfiguration.ExpiryType type, CacheConfiguration.Duration duration) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
