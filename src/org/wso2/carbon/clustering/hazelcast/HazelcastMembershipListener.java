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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.clustering.hazelcast.util.MemberUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * TODO: class description
 */
public class HazelcastMembershipListener implements MembershipListener {
    private static final Log log = LogFactory.getLog(HazelcastMembershipListener.class);
    private Map<String , org.apache.axis2.clustering.Member> members;

    public HazelcastMembershipListener(HazelcastInstance hazelcastInstance, String domain) {
        members = MemberUtils.getMembersMap(hazelcastInstance, domain);
        /*new Thread(){

            @Override
            public void run() {
                while(true){
                    System.out.println("Running...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    for (Map.Entry<String, org.apache.axis2.clustering.Member> entry : members.entrySet()) {
                        System.out.println(entry.getKey() + "=" + entry.getValue());
                    }
                }

            }
        }.start();*/
    }

    @Override
    public void memberAdded(MembershipEvent membershipEvent) {
        Member member = membershipEvent.getMember();
        log.info("Member joined [" +  member.getUuid() + "]: " + member.getInetSocketAddress().toString());
        /*try {
            byte[] buff = new byte[1024];
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(buff));
            member.readData(dataInputStream);
            System.out.println("++++++ MEMBER DATA=" + new String(buff));
            dataInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
*/
    }

    @Override
    public void memberRemoved(MembershipEvent membershipEvent) {
        Member member = membershipEvent.getMember();
        log.info("Member left [" +  member.getUuid() + "]: " + member.getInetSocketAddress().toString());
        members.remove(member.getUuid());
    }
}
