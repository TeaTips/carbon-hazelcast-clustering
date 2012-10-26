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

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.apache.axis2.clustering.ClusteringFault;
import org.apache.axis2.clustering.state.StateClusteringCommand;
import org.apache.axis2.clustering.state.StateManager;
import org.apache.axis2.context.AbstractContext;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.PropertyDifference;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.context.ServiceGroupContext;
import org.apache.axis2.deployment.DeploymentConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * TODO: class description
 */
public class HazelcastStateManager extends ParameterAdapter implements StateManager {
    private static final Log log = LogFactory.getLog(HazelcastStateManager.class);
    private ConfigurationContext configurationContext;
    private final Map<String, List> excludedReplicationPatterns = new HashMap<String, List>();

    private HazelcastInstance hazelcastInstance;
    private IMap<String, Object> configCtxProperties;

    public void init(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;

        configCtxProperties = hazelcastInstance.getMap("$config.context.properties");
        configCtxProperties.addEntryListener(new ConfigContextPropertyEntryListener(), true);
        for (Map.Entry<String, Object> entry : configCtxProperties.entrySet()) {
            configurationContext.setProperty(entry.getKey(), entry.getValue());
        }
    }

    private class ConfigContextPropertyEntryListener implements EntryListener<String, Object> {

        @Override
        public void entryAdded(EntryEvent<String, Object> entryEvent) {
            //To change body of implemented methods use File | Settings | File Templates.
            System.out.println("####### ENTRY ADDED");
            configurationContext.setNonReplicableProperty(entryEvent.getKey(), entryEvent.getValue());
        }

        @Override
        public void entryRemoved(EntryEvent<String, Object> entryEvent) {
            System.out.println("#######  ENTRY removed");
            configurationContext.removePropertyNonReplicable(entryEvent.getKey());
        }

        @Override
        public void entryUpdated(EntryEvent<String, Object> entryEvent) {
            System.out.println("####### ENTRY Updated");
            configurationContext.setNonReplicableProperty(entryEvent.getKey(), entryEvent.getValue());
        }

        @Override
        public void entryEvicted(EntryEvent<String, Object> stringObjectEntryEvent) {
            //To change body of implemented methods use File | Settings | File Templates.
            System.out.println("####### ENTRY Evicted");

        }
    }

    @Override
    public void updateContext(AbstractContext context) throws ClusteringFault {
        if (context instanceof ConfigurationContext) {
            replicateProperties((ConfigurationContext) context, excludedReplicationPatterns, false);
        }
        /*else if (context instanceof ServiceGroupContext) {
            ServiceGroupContext sgCtx = (ServiceGroupContext) context;
//            cmd = new UpdateServiceGroupStateCommand();
//            UpdateServiceGroupStateCommand updateSgCmd = (UpdateServiceGroupStateCommand) cmd;
            updateSgCmd.setServiceGroupName(sgCtx.getDescription().getServiceGroupName());
            updateSgCmd.setServiceGroupContextId(sgCtx.getId());
            IMap<String, Object> map = hazelcastInstance.getMap(sgCtx.getDescription().getServiceGroupName() + "-" + sgCtx.getId());
        }*/

        /*else if (context instanceof ServiceContext) {
            ServiceContext serviceCtx = (ServiceContext) context;
            cmd = new UpdateServiceStateCommand();
            UpdateServiceStateCommand updateServiceCmd = (UpdateServiceStateCommand) cmd;
            String sgName =
                    serviceCtx.getServiceGroupContext().getDescription().getServiceGroupName();
            updateServiceCmd.setServiceGroupName(sgName);
            updateServiceCmd.setServiceGroupContextId(serviceCtx.getServiceGroupContext().getId());
            updateServiceCmd.setServiceName(serviceCtx.getAxisService().getName());
        }*/

    }

    /**
     * @param context                  The context
     * @param excludedPropertyPatterns The property patterns to be excluded from replication
     * @param includeAllProperties     True - Include all properties,
     *                                 False - Include only property differences
     */
    private void replicateProperties(ConfigurationContext context,
                                     Map excludedPropertyPatterns,
                                     boolean includeAllProperties) {
        if (!includeAllProperties) {
            Map diffs = context.getPropertyDifferences();
            for (Object o : diffs.keySet()) {
                String key = (String) o;
                PropertyDifference diff = (PropertyDifference) diffs.get(key);
                Object value = diff.getValue();
                if (isSerializable(value)) {

                    // Next check whether it matches an excluded pattern
                    if (!isExcluded(key,
                                    context.getClass().getName(),
                                    excludedPropertyPatterns)) {
                        if (log.isDebugEnabled()) {
                            log.debug("sending property =" + key + "-" + value);
                        }
                        configCtxProperties.put(diff.getKey(), diff.getValue());
                    }
                }
            }
        } else {
            for (Iterator iter = context.getPropertyNames(); iter.hasNext(); ) {
                String key = (String) iter.next();
                Object value = context.getPropertyNonReplicable(key);
                if (isSerializable(value)) {

                    // Next check whether it matches an excluded pattern
                    if (!isExcluded(key, context.getClass().getName(), excludedPropertyPatterns)) {
                        if (log.isDebugEnabled()) {
                            log.debug("sending property =" + key + "-" + value);
                        }
                        PropertyDifference diff = new PropertyDifference(key, value, false);
                        configCtxProperties.put(diff.getKey(), diff.getValue());
                    }
                }
            }
        }
    }

    private void replicateProperties(ConfigurationContext context,
                                     String[] propertyNames) throws ClusteringFault {
        Map diffs = context.getPropertyDifferences();
        for (String key : propertyNames) {
            Object prop = context.getPropertyNonReplicable(key);

            // First check whether it is serializable
            if (isSerializable(prop)) {
                if (log.isDebugEnabled()) {
                    log.debug("sending property =" + key + "-" + prop);
                }
                PropertyDifference diff = (PropertyDifference) diffs.get(key);
                if (diff != null) {
                    diff.setValue(prop);
                    configCtxProperties.put(diff.getKey(), diff.getValue());

                    // Remove the diff?
                    diffs.remove(key);
                }
            } else {
                String msg =
                        "Trying to replicate non-serializable property " + key +
                        " in context " + context;
                throw new ClusteringFault(msg);
            }
        }
    }

    private static boolean isSerializable(Object obj) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(obj);
            oos.close();
            return out.toByteArray().length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isExcluded(String propertyName,
                                      String ctxClassName,
                                      Map excludedPropertyPatterns) {

        // Check in the excludes list specific to the context
        List specificExcludes =
                (List) excludedPropertyPatterns.get(ctxClassName);
        boolean isExcluded = false;
        if (specificExcludes != null) {
            isExcluded = isExcluded(specificExcludes, propertyName);
        }
        if (!isExcluded) {
            // check in the default excludes
            List defaultExcludes =
                    (List) excludedPropertyPatterns.get(DeploymentConstants.TAG_DEFAULTS);
            if (defaultExcludes != null) {
                isExcluded = isExcluded(defaultExcludes, propertyName);
            }
        }
        return isExcluded;
    }

    private static boolean isExcluded(List list, String propertyName) {
        for (Object aList : list) {
            String pattern = (String) aList;
            if (pattern.startsWith("*")) {
                pattern = pattern.replaceAll("\\*", "");
                if (propertyName.endsWith(pattern)) {
                    return true;
                }
            } else if (pattern.endsWith("*")) {
                pattern = pattern.replaceAll("\\*", "");
                if (propertyName.startsWith(pattern)) {
                    return true;
                }
            } else if (pattern.equals(propertyName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateContext(AbstractContext context, String[] propertyNames) throws ClusteringFault {
        if (context instanceof ConfigurationContext) {
            replicateProperties((ConfigurationContext) context, propertyNames);
        }
    }

    @Override
    public void updateContexts(AbstractContext[] abstractContexts) throws ClusteringFault {
        //To change body of implemented methods use File | Settings | File Templates.
        for (AbstractContext abstractContext : abstractContexts) {
            updateContext(abstractContext);
        }
    }

    @Override
    public void replicateState(StateClusteringCommand stateClusteringCommand) throws ClusteringFault {
        //To change body of implemented methods use File | Settings | File Templates.
        System.out.println("+++ replicateState(StateClusteringCommand stateClusteringCommand)");
    }

    @Override
    public void removeContext(AbstractContext abstractContext) throws ClusteringFault {
        //To change body of implemented methods use File | Settings | File Templates.
        System.out.println("--- removeContext(AbstractContext abstractContext)");
        if (abstractContext instanceof ConfigurationContext) {
//            configCtxProperties.flush();
        }
    }

    @Override
    public boolean isContextClusterable(AbstractContext context) {
        return (context instanceof ConfigurationContext) ||
               (context instanceof ServiceContext) ||
               (context instanceof ServiceGroupContext);
    }

    @Override
    public void setConfigurationContext(ConfigurationContext configurationContext) {
        this.configurationContext = configurationContext;
    }

    public void setReplicationExcludePatterns(String contextType, List patterns) {
        excludedReplicationPatterns.put(contextType, patterns);
    }

    public Map getReplicationExcludePatterns() {
        return excludedReplicationPatterns;
    }
}
