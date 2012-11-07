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

import java.io.Serializable;

/**
 * TODO: class description
 */
public class CacheEntry<V> implements Serializable {

    private static final long serialVersionUID = 1996179870860085427L;

    private V value;
    private long lastAccessed;
    private long lastModified;

    public CacheEntry(V value) {
        this.value = value;
        long now = System.currentTimeMillis();
        this.lastAccessed = now;
        this.lastModified = now;
    }

    public V getValue() {
        lastAccessed = System.currentTimeMillis();
        return value;
    }

    public void setValue(V value) {
        lastModified = System.currentTimeMillis();
        this.value = value;
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheEntry that = (CacheEntry) o;
        return value.equals(that.value);

    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
