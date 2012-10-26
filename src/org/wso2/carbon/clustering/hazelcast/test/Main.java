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

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import org.apache.axis2.clustering.Member;
import org.wso2.carbon.clustering.hazelcast.util.MemberUtils;

/**
 * TODO: class description
 */
public class Main {

    private static Config config;
    private static TcpIpConfig tcpIpConfig;
    private static HazelcastInstance hazelcastInstance;

    public static void main(String[] args) {
        config = new Config();
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.setPort(Integer.parseInt(System.getProperty("port")));
        networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        tcpIpConfig = networkConfig.getJoin().getTcpIpConfig();
        networkConfig.setPublicAddress("127.0.0.1");
        tcpIpConfig.setEnabled(true);
        if (System.getProperty("is.primary") == null) {
            Member member = new Member("127.0.0.1", Integer.parseInt(System.getProperty("member.port")));
            if (MemberUtils.canConnect(member)) {
                tcpIpConfig.addMember("127.0.0.1:" + System.getProperty("member.port"));
            } else {
                startWKAMemberReconnectionTask(member);
            }
        }

        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        hazelcastInstance.getCluster().addMembershipListener(new MembershipListener() {
            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
                System.out.println("member joined");
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
                System.out.println("member left");
            }
        });
    }

    private static void startWKAMemberReconnectionTask(Member wkaMember) {
        new Thread(new WKAMemberAdder(wkaMember)).start();
    }

    private static class WKAMemberAdder implements Runnable {

        private Member wkaMember;

        private WKAMemberAdder(Member wkaMember) {
            this.wkaMember = wkaMember;
        }

        @Override
        public void run() {
            try {
                System.out.println("Started WKA member connection task...");
                while (!MemberUtils.canConnect(wkaMember)) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
                tcpIpConfig.addMember("127.0.0.1:" + System.getProperty("member.port"));
                hazelcastInstance.shutdown();
                hazelcastInstance = Hazelcast.newHazelcastInstance(config);
                hazelcastInstance.getCluster().addMembershipListener(new MembershipListener() {
                    @Override
                    public void memberAdded(MembershipEvent membershipEvent) {
                        System.out.println("member joined");
                    }

                    @Override
                    public void memberRemoved(MembershipEvent membershipEvent) {
                        System.out.println("member left");
                    }
                });
                System.out.println("Added member");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
