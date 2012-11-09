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
import javax.cache.Status;
import javax.cache.mbeans.CacheMXBean;
import java.util.Date;

/**
 * TODO: class description
 */
public class CacheMXBeanImpl implements CacheMXBean {

    private final Cache cache;

    /**
     * Constructor
     *
     * @param cache the cache
     */
    public CacheMXBeanImpl(Cache cache) {
        this.cache = cache;
    }

    @Override
    public void clear() {
        cache.getStatistics().clear();
    }

    @Override
    public Date getStartAccumulationDate() {
        return cache.getStatistics().getStartAccumulationDate();
    }

    @Override
    public long getCacheHits() {
        return cache.getStatistics().getCacheHits();
    }

    @Override
    public float getCacheHitPercentage() {
        return cache.getStatistics().getCacheHitPercentage();
    }

    @Override
    public long getCacheMisses() {
        return cache.getStatistics().getCacheMisses();
    }

    @Override
    public float getCacheMissPercentage() {
        return cache.getStatistics().getCacheMissPercentage();
    }

    @Override
    public long getCacheGets() {
        return cache.getStatistics().getCacheGets();
    }

    @Override
    public long getCachePuts() {
        return cache.getStatistics().getCachePuts();
    }

    @Override
    public long getCacheRemovals() {
        return cache.getStatistics().getCacheRemovals();
    }

    @Override
    public long getCacheEvictions() {
        return cache.getStatistics().getCacheEvictions();
    }

    @Override
    public float getAverageGetMillis() {
        return cache.getStatistics().getAverageGetMillis();
    }

    @Override
    public float getAveragePutMillis() {
        return cache.getStatistics().getAveragePutMillis();
    }

    @Override
    public float getAverageRemoveMillis() {
        return cache.getStatistics().getAverageRemoveMillis();
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public Status getStatus() {
        return cache.getStatus();
    }
}
