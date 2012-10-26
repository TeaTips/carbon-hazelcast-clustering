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

import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.Member;
import org.apache.axis2.clustering.management.GroupManagementAgent;
import org.apache.axis2.clustering.management.GroupManagementCommand;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.clustering.hazelcast.util.MemberUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GroupManagementAgent based on Hazelcast
 */
public class HazelcastGroupManagementAgent implements GroupManagementAgent {
    private static final Log log = LogFactory.getLog(HazelcastGroupManagementAgent.class);
    private String description;
    private IMap<String, Member> members;
    private List<Member> connectedMembers = new CopyOnWriteArrayList<Member>();
    private String localMemberUUID;
    private String domain;
    private String subDomain;
    private ITopic<GroupManagementCommand> groupManagementTopic;
    private int groupMgtPort;
    private TcpIpConfig tcpIpConfig;

    public void init(Config primaryHazelcastConfig,
                     ConfigurationContext configurationContext) {
        NetworkConfig primaryNwConfig = primaryHazelcastConfig.getNetworkConfig();
        Config config = new Config();
        config.setInstanceName(domain);
        NetworkConfig groupNwConfig = config.getNetworkConfig();
        groupNwConfig.setPublicAddress(primaryNwConfig.getPublicAddress());
        groupNwConfig.setPort(groupMgtPort);
        groupNwConfig.getJoin().getMulticastConfig().setEnabled(primaryNwConfig.getJoin().getMulticastConfig().isEnabled());
        groupNwConfig.getJoin().getTcpIpConfig().setEnabled(primaryNwConfig.getJoin().getTcpIpConfig().isEnabled());

        config.setLicenseKey(primaryHazelcastConfig.getLicenseKey());
        config.setManagementCenterConfig(primaryHazelcastConfig.getManagementCenterConfig());


        tcpIpConfig = groupNwConfig.getJoin().getTcpIpConfig();

        GroupConfig groupConfig = config.getGroupConfig();
        groupConfig.setName(domain);
        config.setProperties(primaryHazelcastConfig.getProperties());
        HazelcastInstance hazelcastInstance = Hazelcast.getHazelcastInstanceByName(domain);
        if (hazelcastInstance == null) {
            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        }
        hazelcastInstance.getCluster().addMembershipListener(new GroupMembershipListener());
        localMemberUUID = hazelcastInstance.getCluster().getLocalMember().getUuid();
        Member localMember =
                MemberUtils.getLocalMember(domain, groupNwConfig.getPublicAddress(),
                                           groupMgtPort);
        log.info("Group management local member for domain [" + domain + "],sub-domain [" +
                 subDomain + "] UUID: " + localMemberUUID + ". " + localMember);
        MemberUtils.getMembersMap(hazelcastInstance, domain).put(localMemberUUID, localMember);
        members = MemberUtils.getMembersMap(hazelcastInstance, domain);
        members.addEntryListener(new MemberEntryListener(), true);
        for (Member member : members.values()) {
            connectMember(member);
        }
        groupManagementTopic = hazelcastInstance.getTopic("$" + domain + HazelcastConstants.GROUP_MGT_CMD_TOPIC);
        groupManagementTopic.addMessageListener(new GroupManagementCommandListener(configurationContext));
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setSubDomain(String subDomain) {
        this.subDomain = subDomain;
    }

    @Override
    public void setGroupMgtPort(int groupMgtPort) {
        this.groupMgtPort = groupMgtPort;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void applicationMemberAdded(Member member) {
        // Nothing to implement
    }

    @Override
    public void applicationMemberRemoved(Member member) {
        // Nothing to implement
    }

    @Override
    public List<Member> getMembers() {
        return connectedMembers;
    }

    @Override
    public void send(GroupManagementCommand groupManagementCommand) throws ClusteringFault {
        groupManagementTopic.publish(groupManagementCommand);
    }

    private class GroupMembershipListener implements MembershipListener {

        @Override
        public void memberAdded(MembershipEvent membershipEvent) {
            com.hazelcast.core.Member member = membershipEvent.getMember();
            log.info("Member joined [" + member.getUuid() + "]: " + member.getInetSocketAddress().toString());
        }

        @Override
        public void memberRemoved(MembershipEvent membershipEvent) {
            com.hazelcast.core.Member member = membershipEvent.getMember();
            log.info("Member left [" + member.getUuid() + "]: " + member.getInetSocketAddress().toString());
            Member removed = members.remove(membershipEvent.getMember().getUuid());
            connectedMembers.remove(removed);
        }
    }

    private class MemberEntryListener implements EntryListener<String, Member> {

        @Override
        public void entryAdded(EntryEvent<String, Member> entryEvent) {
            if (entryEvent.getKey().equals(localMemberUUID)) { // Ignore local member
                return;
            }
            Member member = entryEvent.getValue();
            connectMember(member);
            MemberUtils.addMember(entryEvent.getValue(), tcpIpConfig);
        }

        @Override
        public void entryRemoved(EntryEvent<String, Member> entryEvent) {
            connectedMembers.remove(entryEvent.getValue());
        }

        @Override
        public void entryUpdated(EntryEvent<String, Member> entryEvent) {
            // Nothing to do
        }

        @Override
        public void entryEvicted(EntryEvent<String, Member> stringObjectEntryEvent) {
            // Nothing to do
        }
    }

    private void connectMember(Member member) {
        if (!member.getDomain().equals(domain) || !subDomain.equals(member.getProperties().get("subDomain"))) {
            return;
        }
        if (!connectedMembers.contains(member)) {
            Thread th = new Thread(new MemberAdder(member));
            th.setPriority(Thread.MAX_PRIORITY);
            th.start();
        }
    }

    private class MemberAdder implements Runnable {

        private final Member member;

        private MemberAdder(Member member) {
            this.member = member;
        }

        public void run() {
            if (connectedMembers.contains(member)) {
                return;
            }
            if (canConnect(member)) {
                if (!connectedMembers.contains(member)) {
                    connectedMembers.add(member);
                }
                log.info("Application member " + member + " joined application cluster");
            } else {
                log.error("Could not add application member " + member);
            }
        }

        /**
         * Before adding a member, we will try to verify whether we can connect to it
         *
         * @param member The member whose connectvity needs to be verified
         * @return true, if the member can be contacted; false, otherwise.
         */
        private boolean canConnect(Member member) {
            if (log.isDebugEnabled()) {
                log.debug("Trying to connect to member " + member + "...");
            }
            for (int retries = 30; retries > 0; retries--) {
                try {
                    InetAddress addr = InetAddress.getByName(member.getHostName());
                    int httpPort = member.getHttpPort();
                    if (log.isDebugEnabled()) {
                        log.debug("HTTP Port=" + httpPort);
                    }
                    if (httpPort != -1) {
                        SocketAddress httpSockaddr = new InetSocketAddress(addr, httpPort);
                        new Socket().connect(httpSockaddr, 10000);
                    }
                    int httpsPort = member.getHttpsPort();
                    if (log.isDebugEnabled()) {
                        log.debug("HTTPS Port=" + httpsPort);
                    }
                    if (httpsPort != -1) {
                        SocketAddress httpsSockaddr = new InetSocketAddress(addr, httpsPort);
                        new Socket().connect(httpsSockaddr, 10000);
                    }
                    return true;
                } catch (IOException e) {
                    if (log.isDebugEnabled()) {
                        log.debug("", e);
                    }
                    String msg = e.getMessage();
                    if (!msg.contains("Connection refused") && !msg.contains("connect timed out")) {
                        log.error("Cannot connect to member " + member, e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            return false;
        }
    }

    private static class GroupManagementCommandListener implements MessageListener<GroupManagementCommand> {
        private ConfigurationContext configurationContext;

        private GroupManagementCommandListener(ConfigurationContext configurationContext) {
            this.configurationContext = configurationContext;
        }

        @Override
        public void onMessage(Message<GroupManagementCommand> message) {
            GroupManagementCommand command = message.getMessageObject();
            try {
                command.execute(configurationContext);
            } catch (ClusteringFault e) {
                log.error("Cannot execute GroupManagementCommand" + command, e);
            }
        }
    }
}
