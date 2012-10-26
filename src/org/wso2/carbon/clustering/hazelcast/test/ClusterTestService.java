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
package org.wso2.carbon.clustering.hazelcast.test;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO: class description
 */
public class ClusterTestService {

    public int getNextValue(){
        int val = 0;
        ConfigurationContext configurationContext = MessageContext.getCurrentMessageContext().getRootContext();
        AtomicInteger foo =  (AtomicInteger) configurationContext.getProperty("Foo");
        if (foo == null){
            foo = new AtomicInteger(0);
            configurationContext.setProperty("Foo", foo);
        } else {
            val = foo.incrementAndGet();
        }
        return val;
    }
}
