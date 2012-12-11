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
package org.wso2.carbon.clustering.hazelcast.aws;

import com.hazelcast.config.AwsConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.clustering.hazelcast.HazelcastMembershipScheme;

import java.util.Map;

/**
 * TODO: class description
 */
public class AWSBasedMembershipScheme implements HazelcastMembershipScheme {
    private static final Log log = LogFactory.getLog(AWSBasedMembershipScheme.class);
    private final Map<String, Parameter> parameters;
    private final String primaryDomain;
    private final AwsConfig awsConfig;

    public AWSBasedMembershipScheme(Map<String, Parameter> parameters,
                                    String primaryDomain, AwsConfig awsConfig) {
        this.parameters = parameters;
        this.primaryDomain = primaryDomain;
        this.awsConfig = awsConfig;
    }

    @Override
    public void setPrimaryHazelcastInstance(HazelcastInstance primaryHazelcastInstance) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setLocalMember(Member localMember) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void init() throws ClusteringFault {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void joinGroup() throws ClusteringFault {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
