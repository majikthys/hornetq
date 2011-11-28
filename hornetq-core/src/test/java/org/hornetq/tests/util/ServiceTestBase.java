/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;

import junit.framework.Assert;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.client.impl.Topology;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.remoting.impl.invm.InVMRegistry;
import org.hornetq.core.remoting.impl.invm.TransportConstants;
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.server.HornetQComponent;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.core.server.NodeManager;
import org.hornetq.core.server.cluster.ClusterConnection;
import org.hornetq.core.server.impl.HornetQServerImpl;
import org.hornetq.core.settings.impl.AddressFullMessagePolicy;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.spi.core.security.HornetQSecurityManager;
import org.hornetq.spi.core.security.HornetQSecurityManagerImpl;
import org.hornetq.utils.UUIDGenerator;

/**
 *
 * Base class with basic utilities on starting up a basic server
 *
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public abstract class ServiceTestBase extends UnitTestCase
{

   // Constants -----------------------------------------------------

   protected static final long WAIT_TIMEOUT = 10000;


   // Attributes ----------------------------------------------------

   protected static final String INVM_ACCEPTOR_FACTORY = InVMAcceptorFactory.class.getCanonicalName();

   public static final String INVM_CONNECTOR_FACTORY = InVMConnectorFactory.class.getCanonicalName();

   protected static final String NETTY_ACCEPTOR_FACTORY = NettyAcceptorFactory.class.getCanonicalName();

   protected static final String NETTY_CONNECTOR_FACTORY = NettyConnectorFactory.class.getCanonicalName();

   private final List<ServerLocator> locators = new ArrayList<ServerLocator>();

   @Override
   protected void tearDown() throws Exception
   {
      for (ServerLocator locator : locators)
      {
         try
         {
            locator.close();
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
      }
      locators.clear();
      super.tearDown();
//      checkFreePort(5445);
//      checkFreePort(5446);
//      checkFreePort(5447);
      if (InVMRegistry.instance.size() > 0)
      {
         fail("InVMREgistry size > 0");
      }
   }

   public static final void closeServerLocator(ServerLocator locator)
   {
      if (locator == null)
         return;
      try
      {
         locator.close();
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   protected void waitForTopology(final HornetQServer server, final int nodes) throws Exception
   {
      waitForTopology(server, nodes, WAIT_TIMEOUT);
   }

   protected void waitForTopology(final HornetQServer server, final int nodes, final long timeout) throws Exception
   {
      log.debug("waiting for " + nodes + " on the topology for server = " + server);

      long start = System.currentTimeMillis();

      Set<ClusterConnection> ccs = server.getClusterManager().getClusterConnections();

      if (ccs.size() != 1)
      {
         throw new IllegalStateException("You need a single cluster connection on this version of waitForTopology on ServiceTestBase");
      }

      Topology topology = ccs.iterator().next().getTopology();

      do
      {
         if (nodes == topology.getMembers().size())
         {
            return;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for cluster topology of " + nodes +
                   " (received " +
                   topology.getMembers().size() +
                   ") topology = " +
                   topology +
                   ")";

      log.error(msg);

      throw new Exception(msg);
   }


   protected void waitForTopology(final HornetQServer server, String clusterConnectionName, final int nodes, final long timeout) throws Exception
   {
      log.debug("waiting for " + nodes + " on the topology for server = " + server);

      long start = System.currentTimeMillis();

      ClusterConnection clusterConnection = server.getClusterManager().getClusterConnection(clusterConnectionName);


      Topology topology = clusterConnection.getTopology();

      do
      {
         if (nodes == topology.getMembers().size())
         {
            return;
         }

         Thread.sleep(10);
      }
      while (System.currentTimeMillis() - start < timeout);

      String msg = "Timed out waiting for cluster topology of " + nodes +
                   " (received " +
                   topology.getMembers().size() +
                   ") topology = " +
                   topology +
                   ")";

      log.error(msg);

      throw new Exception(msg);
   }

   protected final static void waitForComponent(final HornetQComponent component, final long seconds) throws Exception
   {
      long time = System.currentTimeMillis();
      long toWait = seconds * 1000;
      while (!component.isStarted())
      {
         try
         {
            Thread.sleep(50);
         }
         catch (InterruptedException e)
         {
            // ignore
         }
         if (System.currentTimeMillis() > (time + toWait))
         {
            fail("component did not start within timeout of " + seconds);
         }
      }
   }

   protected static final void stopComponent(HornetQComponent component)
   {
      if (component == null)
         return;
      if (component.isStarted())
         try
         {
            component.stop();
         }
         catch (Exception e)
         {
            // no-op
         }
   }

   protected static Map<String, Object> generateParams(final int node, final boolean netty)
   {
      Map<String, Object> params = new HashMap<String, Object>();

      if (netty)
      {
         params.put(org.hornetq.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME,
                    org.hornetq.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + node);
      }
      else
      {
         params.put(org.hornetq.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, node);
      }

      return params;
   }

   protected static TransportConfiguration createTransportConfiguration(boolean netty,
                                                                        boolean acceptor,
                                                                        Map<String, Object> params)
   {
      String className;
      if (netty)
      {
         if (acceptor)
         {
            className = NETTY_ACCEPTOR_FACTORY;
         }
         else
         {
            className = NETTY_CONNECTOR_FACTORY;
         }
      }
      else
      {
         if (acceptor)
         {
            className = INVM_ACCEPTOR_FACTORY;
         }
         else
         {
            className = INVM_CONNECTOR_FACTORY;
         }
      }
      return new TransportConfiguration(className, params);
   }

   // Static --------------------------------------------------------
   private final Logger log = Logger.getLogger(this.getClass());

   // Constructors --------------------------------------------------

   public ServiceTestBase()
   {
      super();
   }

   public ServiceTestBase(final String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected void waitForServer(HornetQServer server) throws InterruptedException
   {
      if (server == null)
         return;
      long timetowait = System.currentTimeMillis() + 5000;
      while (!server.isStarted() && System.currentTimeMillis() < timetowait)
      {
         Thread.sleep(50);
      }

      if (!server.isStarted())
      {
         log.info(threadDump("Server didn't start"));
         fail("server didnt start: " + server);
      }

      if (!server.getConfiguration().isBackup())
      {
         timetowait = System.currentTimeMillis() + 5000;
         while (!server.isInitialised() && System.currentTimeMillis() < timetowait)
         {
            Thread.sleep(50);
         }

         if (!server.isInitialised())
         {
            fail("Server didn't initialize: " + server);
         }
      }
   }


   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final int pageSize,
                                        final int maxAddressSize,
                                        final Map<String, AddressSettings> settings,
                                        final MBeanServer mbeanServer)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer, true);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer, false);
      }

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(pageSize);
      defaultSetting.setMaxSizeBytes(maxAddressSize);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final int pageSize,
                                        final int maxAddressSize,
                                        final Map<String, AddressSettings> settings)
   {
      return createServer(realFiles, configuration, pageSize, maxAddressSize, AddressFullMessagePolicy.PAGE, settings);
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final int pageSize,
                                        final int maxAddressSize,
                                        final AddressFullMessagePolicy fullPolicy,
                                        final Map<String, AddressSettings> settings)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration, false);
      }

      if (settings != null)
      {
         for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
         {
            server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
         }
      }

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(pageSize);
      defaultSetting.setMaxSizeBytes(maxAddressSize);
      defaultSetting.setAddressFullMessagePolicy(fullPolicy);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final MBeanServer mbeanServer,
                                        final Map<String, AddressSettings> settings)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration, mbeanServer, false);
      }

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();
      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles)
   {
      return createServer(realFiles, false);
   }

   protected HornetQServer createServer(final boolean realFiles, final boolean netty)
   {
      return createServer(realFiles, createDefaultConfig(netty), -1, -1, new HashMap<String, AddressSettings>());
   }

   protected HornetQServer createServer(final boolean realFiles, final Configuration configuration)
   {
      return createServer(realFiles, configuration, -1, -1, new HashMap<String, AddressSettings>());
   }

   protected HornetQServer createInVMFailoverServer(final boolean realFiles,
                                                    final Configuration configuration,
                                                    final NodeManager nodeManager,
                                                    final int id)
   {
      return createInVMFailoverServer(realFiles,
                                      configuration,
                                      -1,
                                      -1,
                                      new HashMap<String, AddressSettings>(),
                                      nodeManager,
                                      id);
   }

   protected HornetQServer createInVMFailoverServer(final boolean realFiles,
                                                    final Configuration configuration,
                                                    final int pageSize,
                                                    final int maxAddressSize,
                                                    final Map<String, AddressSettings> settings,
                                                    NodeManager nodeManager,
                                                    final int id)
   {
      HornetQServer server;
      HornetQSecurityManager securityManager = new HornetQSecurityManagerImpl();
      configuration.setPersistenceEnabled(realFiles);
      server = new InVMNodeManagerServer(configuration,
                                         ManagementFactory.getPlatformMBeanServer(),
                                         securityManager,
                                         nodeManager);

      server.setIdentity("Server " + id);

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();
      defaultSetting.setPageSizeBytes(pageSize);
      defaultSetting.setMaxSizeBytes(maxAddressSize);

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createServer(final boolean realFiles,
                                        final Configuration configuration,
                                        final HornetQSecurityManager securityManager)
   {
      HornetQServer server;

      if (realFiles)
      {
         server = HornetQServers.newHornetQServer(configuration,
                                                  ManagementFactory.getPlatformMBeanServer(),
                                                  securityManager);
      }
      else
      {
         server = HornetQServers.newHornetQServer(configuration,
                                                  ManagementFactory.getPlatformMBeanServer(),
                                                  securityManager,
                                                  false);
      }

      Map<String, AddressSettings> settings = new HashMap<String, AddressSettings>();

      for (Map.Entry<String, AddressSettings> setting : settings.entrySet())
      {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }

      AddressSettings defaultSetting = new AddressSettings();

      server.getAddressSettingsRepository().addMatch("#", defaultSetting);

      return server;
   }

   protected HornetQServer createClusteredServerWithParams(final boolean isNetty,
                                                           final int index,
                                                           final boolean realFiles,
                                                           final Map<String, Object> params)
   {
      if (isNetty)
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, NETTY_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
      else
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, INVM_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
   }

   protected HornetQServer createClusteredServerWithParams(final boolean isNetty,
                                                           final int index,
                                                           final boolean realFiles,
                                                           final int pageSize,
                                                           final int maxAddressSize,
                                                           final Map<String, Object> params)
   {
      if (isNetty)
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, NETTY_ACCEPTOR_FACTORY),
                             pageSize,
                             maxAddressSize,
                             new HashMap<String, AddressSettings>());
      }
      else
      {
         return createServer(realFiles,
                             createClusteredDefaultConfig(index, params, INVM_ACCEPTOR_FACTORY),
                             -1,
                             -1,
                             new HashMap<String, AddressSettings>());
      }
   }

   protected ServerLocator createFactory(final boolean isNetty) throws Exception
   {
      if (isNetty)
      {
         return createNettyNonHALocator();
      }
      else
      {
         return createInVMNonHALocator();
      }
   }

   protected void createQueue(final String address, final String queue) throws Exception
   {
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = locator.createSessionFactory();
      ClientSession session = sf.createSession();
      session.createQueue(address, queue);
      session.close();
      sf.close();
      locator.close();
   }

   protected ServerLocator createInVMNonHALocator()
   {
      return createNonHALocator(false);
   }

   protected ServerLocator createNettyNonHALocator()
   {
      return createNonHALocator(true);
   }

   protected ServerLocator createNonHALocator(final boolean isNetty)
   {
      ServerLocator locatorWithoutHA = isNetty ? HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(NETTY_CONNECTOR_FACTORY))
                                              : HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      locators.add(locatorWithoutHA);
      return locatorWithoutHA;
   }

   protected ServerLocator createInVMLocator(final int serverID)
   {
      TransportConfiguration tnspConfig = createInVMTransportConnectorConfig(serverID, UUIDGenerator.getInstance().generateStringUUID());

      return HornetQClient.createServerLocatorWithHA(tnspConfig);
   }

   /**
    * @param serverID
    * @return
    */
   protected TransportConfiguration createInVMTransportConnectorConfig(final int serverID, String name)
   {
      Map<String, Object> server1Params = new HashMap<String, Object>();

      if (serverID != 0)
      {
         server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, serverID);
      }

      TransportConfiguration tnspConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY, server1Params, name);
      return tnspConfig;
   }

   // XXX unused
   protected ClientSessionFactoryImpl createFactory(final String connectorClass) throws Exception
   {
      ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(connectorClass));
      return (ClientSessionFactoryImpl)locator.createSessionFactory();

   }
   public String getTextMessage(final ClientMessage m)
   {
      m.getBodyBuffer().resetReaderIndex();
      return m.getBodyBuffer().readString();
   }

   protected ClientMessage createBytesMessage(final ClientSession session,
                                              final byte type,
                                              final byte[] b,
                                              final boolean durable)
   {
      ClientMessage message = session.createMessage(type, durable, 0, System.currentTimeMillis(), (byte)1);
      message.getBodyBuffer().writeBytes(b);
      return message;
   }

   /**
    * @param i
    * @param message
    * @throws Exception
    */
   protected void setBody(final int i, final ClientMessage message) throws Exception
   {
      message.getBodyBuffer().writeString("message" + i);
   }

   /**
    * @param i
    * @param message
    */
   protected void assertMessageBody(final int i, final ClientMessage message)
   {
      Assert.assertEquals(message.toString(), "message" + i, message.getBodyBuffer().readString());
   }

   /**
    * Send durable messages with pre-specified body.
    * @param session
    * @param producer
    * @param numMessages
    * @throws Exception
    */
   public final void sendMessages(ClientSession session, ClientProducer producer, int numMessages) throws Exception
   {
      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);
         setBody(i, message);
         message.putIntProperty("counter", i);
         producer.send(message);
      }
   }

   protected final void
            receiveMessages(ClientConsumer consumer, final int start, final int msgCount, final boolean ack)
                                                                                                            throws HornetQException
   {
      for (int i = start; i < msgCount; i++)
      {
         ClientMessage message = consumer.receive(1000);
         Assert.assertNotNull("Expecting a message " + i, message);
         assertMessageBody(i, message);
         Assert.assertEquals(i, message.getIntProperty("counter").intValue());
         if (ack)
            message.acknowledge();
      }
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. We need to keep looking for a while
    * if the file hasn't been deleted yet.
    */
   protected void validateNoFilesOnLargeDir(final int expect) throws Exception
   {
      File largeMessagesFileDir = new File(getLargeMessagesDir());

      // Deleting the file is async... we keep looking for a period of the time until the file is really gone
      long timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis() && largeMessagesFileDir.listFiles().length != expect)
      {
         Thread.sleep(100);
      }


      if (expect != largeMessagesFileDir.listFiles().length)
      {
         for (File file : largeMessagesFileDir.listFiles())
         {
            System.out.println("File " + file + " still on ");
         }
      }

      Assert.assertEquals(expect, largeMessagesFileDir.listFiles().length);
   }

   /**
    * Deleting a file on LargeDire is an asynchronous process. Wee need to keep looking for a while
    * if the file hasn't been deleted yet
    */
   protected void validateNoFilesOnLargeDir() throws Exception
   {
      validateNoFilesOnLargeDir(0);
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
   public final class InVMNodeManagerServer extends HornetQServerImpl
   {
      final NodeManager nodeManager;

      public InVMNodeManagerServer(final NodeManager nodeManager)
      {
         super();
         this.nodeManager = nodeManager;
      }

      public InVMNodeManagerServer(final Configuration configuration, final NodeManager nodeManager)
      {
         super(configuration);
         this.nodeManager = nodeManager;
      }

      public InVMNodeManagerServer(final Configuration configuration,
                                   final MBeanServer mbeanServer,
                                   final NodeManager nodeManager)
      {
         super(configuration, mbeanServer);
         this.nodeManager = nodeManager;
      }

      public InVMNodeManagerServer(final Configuration configuration,
                                   final HornetQSecurityManager securityManager,
                                   final NodeManager nodeManager)
      {
         super(configuration, securityManager);
         this.nodeManager = nodeManager;
      }

      public InVMNodeManagerServer(final Configuration configuration,
                                   final MBeanServer mbeanServer,
                                   final HornetQSecurityManager securityManager,
                                   final NodeManager nodeManager)
      {
         super(configuration, mbeanServer, securityManager);
         this.nodeManager = nodeManager;
      }

      @Override
      protected NodeManager createNodeManager(final String directory)
      {
         return nodeManager;
      }

   }
}
