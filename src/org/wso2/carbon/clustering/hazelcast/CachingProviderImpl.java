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
package org.wso2.carbon.clustering.hazelcast;

import org.wso2.carbon.clustering.hazelcast.jsr107.CacheManagerFactoryImpl;

import javax.cache.CacheManagerFactory;
import javax.cache.OptionalFeature;
import javax.cache.spi.CachingProvider;

/**
 * TODO: class description
 */
public class CachingProviderImpl implements CachingProvider {

    public CachingProviderImpl() {
    }

    @Override
    public CacheManagerFactory getCacheManagerFactory() {
        return new CacheManagerFactoryImpl();
    }

    @Override
    public boolean isSupported(OptionalFeature optionalFeature) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}