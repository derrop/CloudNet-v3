package de.dytanic.cloudnet;

import de.dytanic.cloudnet.cluster.DefaultClusterNodeServerProvider;
import de.dytanic.cloudnet.cluster.IClusterNodeServer;
import de.dytanic.cloudnet.cluster.IClusterNodeServerProvider;
import de.dytanic.cloudnet.command.ConsoleCommandSender;
import de.dytanic.cloudnet.command.DefaultCommandMap;
import de.dytanic.cloudnet.command.DriverCommandSender;
import de.dytanic.cloudnet.command.ICommandMap;
import de.dytanic.cloudnet.command.commands.*;
import de.dytanic.cloudnet.command.jline2.JLine2CommandCompleter;
import de.dytanic.cloudnet.common.Properties;
import de.dytanic.cloudnet.common.Validate;
import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.common.collection.Maps;
import de.dytanic.cloudnet.common.collection.Pair;
import de.dytanic.cloudnet.common.concurrent.DefaultTaskScheduler;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.concurrent.ITaskScheduler;
import de.dytanic.cloudnet.common.concurrent.ListenableTask;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.common.io.FileUtils;
import de.dytanic.cloudnet.common.language.LanguageManager;
import de.dytanic.cloudnet.common.logging.ILogger;
import de.dytanic.cloudnet.common.logging.LogLevel;
import de.dytanic.cloudnet.common.unsafe.CPUUsageResolver;
import de.dytanic.cloudnet.conf.IConfiguration;
import de.dytanic.cloudnet.conf.IConfigurationRegistry;
import de.dytanic.cloudnet.conf.JsonConfiguration;
import de.dytanic.cloudnet.conf.JsonConfigurationRegistry;
import de.dytanic.cloudnet.console.ConsoleColor;
import de.dytanic.cloudnet.console.IConsole;
import de.dytanic.cloudnet.console.JLine2Console;
import de.dytanic.cloudnet.database.AbstractDatabaseProvider;
import de.dytanic.cloudnet.database.DefaultDatabaseHandler;
import de.dytanic.cloudnet.database.IDatabase;
import de.dytanic.cloudnet.database.h2.H2DatabaseProvider;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.DriverEnvironment;
import de.dytanic.cloudnet.driver.event.events.instance.CloudNetTickEvent;
import de.dytanic.cloudnet.driver.module.DefaultPersistableModuleDependencyLoader;
import de.dytanic.cloudnet.driver.module.IModuleWrapper;
import de.dytanic.cloudnet.driver.network.HostAndPort;
import de.dytanic.cloudnet.driver.network.INetworkChannel;
import de.dytanic.cloudnet.driver.network.INetworkClient;
import de.dytanic.cloudnet.driver.network.INetworkServer;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNode;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNodeExtensionSnapshot;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNodeInfoSnapshot;
import de.dytanic.cloudnet.driver.network.def.PacketConstants;
import de.dytanic.cloudnet.driver.network.def.packet.PacketClientServerChannelMessage;
import de.dytanic.cloudnet.driver.network.http.IHttpServer;
import de.dytanic.cloudnet.driver.network.netty.NettyHttpServer;
import de.dytanic.cloudnet.driver.network.netty.NettyNetworkClient;
import de.dytanic.cloudnet.driver.network.netty.NettyNetworkServer;
import de.dytanic.cloudnet.driver.network.protocol.IPacket;
import de.dytanic.cloudnet.driver.permission.*;
import de.dytanic.cloudnet.driver.service.*;
import de.dytanic.cloudnet.event.CloudNetNodePostInitializationEvent;
import de.dytanic.cloudnet.event.cluster.NetworkClusterNodeInfoConfigureEvent;
import de.dytanic.cloudnet.event.command.CommandNotFoundEvent;
import de.dytanic.cloudnet.event.command.CommandPostProcessEvent;
import de.dytanic.cloudnet.event.command.CommandPreProcessEvent;
import de.dytanic.cloudnet.event.permission.PermissionServiceSetEvent;
import de.dytanic.cloudnet.log.QueuedConsoleLogHandler;
import de.dytanic.cloudnet.module.NodeModuleProviderHandler;
import de.dytanic.cloudnet.network.NetworkClientChannelHandlerImpl;
import de.dytanic.cloudnet.network.NetworkServerChannelHandlerImpl;
import de.dytanic.cloudnet.network.listener.*;
import de.dytanic.cloudnet.network.packet.*;
import de.dytanic.cloudnet.permission.DefaultDatabasePermissionManagement;
import de.dytanic.cloudnet.permission.DefaultPermissionManagementHandler;
import de.dytanic.cloudnet.permission.command.DefaultPermissionUserCommandSender;
import de.dytanic.cloudnet.permission.command.IPermissionUserCommandSender;
import de.dytanic.cloudnet.service.DefaultCloudServiceManager;
import de.dytanic.cloudnet.service.ICloudService;
import de.dytanic.cloudnet.service.ICloudServiceManager;
import de.dytanic.cloudnet.template.ITemplateStorage;
import de.dytanic.cloudnet.template.LocalTemplateStorage;
import lombok.Getter;

import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CloudNet extends CloudNetDriver {

    public static volatile boolean RUNNING = true;

    public static final int TPS = 10;

    private static CloudNet instance;

    /*= --------------------------------------------------------------------------------------------------- =*/

    @Getter
    private final ICommandMap commandMap = new DefaultCommandMap();

    @Getter
    private final File moduleDirectory = new File(System.getProperty("cloudnet.modules.directory", "modules"));

    @Getter
    private final IConfiguration config = new JsonConfiguration();

    @Getter
    private final IConfigurationRegistry configurationRegistry = new JsonConfigurationRegistry(Paths.get(System.getProperty("cloudnet.registry.global.path", "local/registry")));

    @Getter
    private final ICloudServiceManager cloudServiceManager = new DefaultCloudServiceManager();

    @Getter
    private final IClusterNodeServerProvider clusterNodeServerProvider = new DefaultClusterNodeServerProvider();

    @Getter
    private final ITaskScheduler networkTaskScheduler = new DefaultTaskScheduler();

    /*= ----------------------------------------------------------- =*/

    @Getter
    private final List<String> commandLineArguments;

    @Getter
    private final Properties commandLineProperties;

    @Getter
    private final IConsole console;

    @Getter
    private final QueuedConsoleLogHandler queuedConsoleLogHandler;

    @Getter
    private final ConsoleCommandSender consoleCommandSender;

    /*= ----------------------------------------------------------- =*/

    @Getter
    private INetworkClient networkClient;

    @Getter
    private INetworkServer networkServer;

    @Getter
    private IHttpServer httpServer;

    @Getter
    private IPermissionManagement permissionManagement;

    @Getter
    private AbstractDatabaseProvider databaseProvider;

    /*= ----------------------------------------------------------- =*/

    @Getter
    private volatile NetworkClusterNodeInfoSnapshot lastNetworkClusterNodeInfoSnapshot, currentNetworkClusterNodeInfoSnapshot;

    private final Queue<ITask<?>> processQueue = Iterables.newConcurrentLinkedQueue();

    CloudNet(List<String> commandLineArguments, ILogger logger, IConsole console)
    {
        super(logger);
        setInstance(this);

        this.console = console;
        this.commandLineArguments = commandLineArguments;
        this.commandLineProperties = Properties.parseLine(commandLineArguments.toArray(new String[0]));

        this.consoleCommandSender = new ConsoleCommandSender(logger);

        logger.addLogHandler(queuedConsoleLogHandler = new QueuedConsoleLogHandler());

        this.moduleProvider.setModuleProviderHandler(new NodeModuleProviderHandler());
        this.moduleProvider.setModuleDependencyLoader(new DefaultPersistableModuleDependencyLoader(new File(System.getProperty("cloudnet.launcher.dir", "launcher") + "/libs")));

        this.driverEnvironment = DriverEnvironment.CLOUDNET;
    }

    public static CloudNet getInstance()
    {
        if (instance == null)
            instance = (CloudNet) CloudNetDriver.getInstance();

        return instance;
    }

    @Override
    public synchronized void start() throws Exception
    {
        File tempDirectory = new File(System.getProperty("cloudnet.tempDir", "temp"));
        tempDirectory.mkdirs();

        new File(tempDirectory, "caches").mkdir();

        try (InputStream inputStream = CloudNet.class.getClassLoader().getResourceAsStream("wrapper.jar"))
        {
            Files.copy(inputStream, new File(tempDirectory, "caches/wrapper.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        initDefaultConfigDefaultHostAddress();
        this.config.load();

        this.networkClient = new NettyNetworkClient(NetworkClientChannelHandlerImpl::new,
            this.config.getClientSslConfig().isEnabled() ? this.config.getClientSslConfig().toSslConfiguration() : null,
            networkTaskScheduler
        );
        this.networkServer = new NettyNetworkServer(NetworkServerChannelHandlerImpl::new,
            this.config.getClientSslConfig().isEnabled() ? this.config.getServerSslConfig().toSslConfiguration() : null,
            networkTaskScheduler
        );
        this.httpServer = new NettyHttpServer(this.config.getClientSslConfig().isEnabled() ? this.config.getWebSslConfig().toSslConfiguration() : null);

        this.initPacketRegistryListeners();
        this.clusterNodeServerProvider.setClusterServers(this.config.getClusterConfig());

        this.enableCommandCompleter();
        this.setDefaultRegistryEntries();

        this.registerDefaultCommands();
        this.registerDefaultServices();

        this.currentNetworkClusterNodeInfoSnapshot = createClusterNodeInfoSnapshot();
        this.lastNetworkClusterNodeInfoSnapshot = currentNetworkClusterNodeInfoSnapshot;

        this.loadModules();

        this.databaseProvider = this.servicesRegistry.getService(AbstractDatabaseProvider.class,
            this.configurationRegistry.getString("database_provider", "h2"));

        if (databaseProvider == null) stop();

        this.databaseProvider.setDatabaseHandler(new DefaultDatabaseHandler());

        if (!this.databaseProvider.init() && !(this.databaseProvider instanceof H2DatabaseProvider))
        {
            this.databaseProvider = this.servicesRegistry.getService(AbstractDatabaseProvider.class, "h2");
            this.databaseProvider.init();
        }

        this.permissionManagement = this.servicesRegistry.getService(IPermissionManagement.class, this.configurationRegistry.getString("permission_service", "json_database"));
        this.permissionManagement.setPermissionManagementHandler(new DefaultPermissionManagementHandler());

        this.startModules();
        this.eventManager.callEvent(new PermissionServiceSetEvent(this.permissionManagement));

        this.setNetworkListeners();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Shutdown Thread"));

        //setup implementations
        this.initDefaultPermissionGroups();
        this.initDefaultTasks();

        eventManager.callEvent(new CloudNetNodePostInitializationEvent());

        this.runConsole();
        this.start0();
    }

    private void setNetworkListeners()
    {
        Random random = new Random();
        for (NetworkClusterNode node : this.config.getClusterConfig().getNodes())
            if (!networkClient.connect(node.getListeners()[random.nextInt(node.getListeners().length)]))
                this.logger.log(LogLevel.WARNING, LanguageManager.getMessage("cluster-server-networking-connection-refused"));

        for (HostAndPort hostAndPort : this.config.getIdentity().getListeners())
        {
            this.logger.info(LanguageManager.getMessage("cloudnet-network-server-bind").replace("%address%",
                hostAndPort.getHost() + ":" + hostAndPort.getPort()));

            this.networkServer.addListener(hostAndPort);
        }

        for (HostAndPort hostAndPort : this.config.getHttpListeners())
        {
            this.logger.info(LanguageManager.getMessage("cloudnet-http-server-bind").replace("%address%",
                hostAndPort.getHost() + ":" + hostAndPort.getPort()));

            this.httpServer.addListener(hostAndPort);
        }
    }

    public void reload()
    {
        this.logger.info(LanguageManager.getMessage("reload-start-message"));

        this.config.load();
        this.getConfigurationRegistry().load();
        this.clusterNodeServerProvider.setClusterServers(this.config.getClusterConfig());

        this.cloudServiceManager.reload();

        this.unloadAll();

        this.registerDefaultCommands();
        this.registerDefaultServices();
        this.enableModules();

        this.logger.info(LanguageManager.getMessage("reload-end-message"));
    }

    @Override
    public void stop()
    {
        if (RUNNING) RUNNING = false;
        else return;

        this.logger.info(LanguageManager.getMessage("stop-start-message"));

        this.cloudServiceManager.deleteAllCloudServices();
        this.taskScheduler.shutdown();

        this.unloadAll();
        this.unloadAllModules0();

        try
        {
            if (this.databaseProvider != null)
                try
                {
                    this.databaseProvider.close();
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }

            this.logger.info(LanguageManager.getMessage("stop-network-client"));
            this.networkClient.close();

            this.logger.info(LanguageManager.getMessage("stop-network-server"));
            this.networkServer.close();

            this.logger.info(LanguageManager.getMessage("stop-http-server"));
            this.httpServer.close();

            this.networkTaskScheduler.shutdown();

        } catch (Exception ex)
        {
            ex.printStackTrace();
        }

        FileUtils.delete(new File("temp"));

        if (!Thread.currentThread().getName().equals("Shutdown Thread"))
            System.exit(0);
    }

    @Override
    public String[] sendCommandLine(String commandLine)
    {
        Validate.checkNotNull(commandLine);

        Collection<String> collection = Iterables.newArrayList();

        if (this.isMainThread())
            this.sendCommandLine0(collection, commandLine);
        else
            try
            {
                runTask(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception
                    {
                        sendCommandLine0(collection, commandLine);
                        return null;
                    }
                }).get();
            } catch (InterruptedException | ExecutionException e)
            {
                e.printStackTrace();
            }

        return collection.toArray(new String[0]);
    }

    @Override
    public String[] sendCommandLine(String nodeUniqueId, String commandLine)
    {
        Validate.checkNotNull(nodeUniqueId);
        Validate.checkNotNull(commandLine);

        if (this.getConfig().getIdentity().getUniqueId().equals(nodeUniqueId))
            return this.sendCommandLine(commandLine);

        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(nodeUniqueId);

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.sendCommandLine(commandLine);

        return null;
    }

    private void sendCommandLine0(Collection<String> collection, String commandLine)
    {
        this.commandMap.dispatchCommand(new DriverCommandSender(collection), commandLine);
    }

    @Override
    public void sendChannelMessage(String channel, String message, JsonDocument data)
    {
        Validate.checkNotNull(channel);
        Validate.checkNotNull(message);
        Validate.checkNotNull(data);

        this.sendAll(new PacketClientServerChannelMessage(channel, message, data));
    }

    @Override
    public ServiceInfoSnapshot createCloudService(ServiceTask serviceTask)
    {
        Validate.checkNotNull(serviceTask);

        try
        {
            NetworkClusterNodeInfoSnapshot networkClusterNodeInfoSnapshot = searchLogicNode(serviceTask);

            if (getConfig().getIdentity().getUniqueId().equals(networkClusterNodeInfoSnapshot.getNode().getUniqueId()))
            {
                ICloudService cloudService = this.cloudServiceManager.runTask(serviceTask);
                return cloudService != null ? cloudService.getServiceInfoSnapshot() : null;
            } else
            {
                IClusterNodeServer clusterNodeServer = getClusterNodeServerProvider().getNodeServer(networkClusterNodeInfoSnapshot.getNode().getUniqueId());

                if (clusterNodeServer != null && clusterNodeServer.isConnected())
                    return clusterNodeServer.createCloudService(serviceTask);
            }

        } catch (Exception ignored)
        {
        }

        return null;
    }

    @Override
    public ServiceInfoSnapshot createCloudService(ServiceConfiguration serviceConfiguration)
    {
        Validate.checkNotNull(serviceConfiguration);

        if (serviceConfiguration.getServiceId() == null || serviceConfiguration.getServiceId().getNodeUniqueId() == null)
            return null;

        if (getConfig().getIdentity().getUniqueId().equals(serviceConfiguration.getServiceId().getNodeUniqueId()))
        {
            ICloudService cloudService = this.cloudServiceManager.runTask(serviceConfiguration);
            return cloudService != null ? cloudService.getServiceInfoSnapshot() : null;
        } else
        {
            IClusterNodeServer clusterNodeServer = getClusterNodeServerProvider().getNodeServer(serviceConfiguration.getServiceId().getNodeUniqueId());

            if (clusterNodeServer != null && clusterNodeServer.isConnected())
                return clusterNodeServer.createCloudService(serviceConfiguration);
        }

        return null;
    }

    @Override
    public ServiceInfoSnapshot createCloudService(String name, String runtime, boolean autoDeleteOnStop, boolean staticService, Collection<ServiceRemoteInclusion> includes,
                                                  Collection<ServiceTemplate> templates, Collection<ServiceDeployment> deployments,
                                                  Collection<String> groups, ProcessConfiguration processConfiguration, Integer port)
    {
        ICloudService cloudService = this.cloudServiceManager.runTask(name, runtime, autoDeleteOnStop, staticService, includes, templates, deployments, groups, processConfiguration, port);
        return cloudService != null ? cloudService.getServiceInfoSnapshot() : null;
    }

    @Override
    public Collection<ServiceInfoSnapshot> createCloudService(String nodeUniqueId, int amount, String name, String runtime, boolean autoDeleteOnStop, boolean staticService,
                                                              Collection<ServiceRemoteInclusion> includes, Collection<ServiceTemplate> templates,
                                                              Collection<ServiceDeployment> deployments, Collection<String> groups, ProcessConfiguration processConfiguration, Integer port)
    {
        Validate.checkNotNull(nodeUniqueId);
        Validate.checkNotNull(name);
        Validate.checkNotNull(includes);
        Validate.checkNotNull(templates);
        Validate.checkNotNull(deployments);
        Validate.checkNotNull(groups);
        Validate.checkNotNull(processConfiguration);

        if (this.getConfig().getIdentity().getUniqueId().equals(nodeUniqueId))
        {
            Collection<ServiceInfoSnapshot> collection = Iterables.newArrayList();

            for (int i = 0; i < amount; i++)
            {
                ICloudService cloudService = this.cloudServiceManager.runTask(
                    name, runtime, autoDeleteOnStop, staticService, includes, templates, deployments, groups, processConfiguration, port != null ? port++ : null
                );

                if (cloudService != null) collection.add(cloudService.getServiceInfoSnapshot());
            }

            return collection;
        }

        IClusterNodeServer clusterNodeServer = getClusterNodeServerProvider().getNodeServer(nodeUniqueId);

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.createCloudService(nodeUniqueId, amount, name, runtime, autoDeleteOnStop, staticService, includes, templates, deployments, groups, processConfiguration, port);
        else
            return null;
    }

    @Override
    public ServiceInfoSnapshot sendCommandLineToCloudService(UUID uniqueId, String commandLine)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(commandLine);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return null;

        ICloudService cloudService = cloudServiceManager.getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.runCommand(commandLine);
            return cloudService.getServiceInfoSnapshot();
        }

        ServiceInfoSnapshot serviceInfoSnapshot = this.getCloudServiceManager().getServiceInfoSnapshot(uniqueId);
        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.sendCommandLineToCloudService(uniqueId, commandLine);

        return null;
    }

    @Override
    public ServiceInfoSnapshot addServiceTemplateToCloudService(UUID uniqueId, ServiceTemplate serviceTemplate)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceTemplate);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return null;

        ICloudService cloudService = cloudServiceManager.getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.getWaitingTemplates().offer(serviceTemplate);
            return cloudService.getServiceInfoSnapshot();
        }

        ServiceInfoSnapshot serviceInfoSnapshot = this.getCloudServiceManager().getServiceInfoSnapshot(uniqueId);
        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.addServiceTemplateToCloudService(uniqueId, serviceTemplate);

        return null;
    }

    @Override
    public ServiceInfoSnapshot addServiceRemoteInclusionToCloudService(UUID uniqueId, ServiceRemoteInclusion serviceRemoteInclusion)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceRemoteInclusion);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return null;

        ICloudService cloudService = cloudServiceManager.getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.getWaitingIncludes().offer(serviceRemoteInclusion);
            return cloudService.getServiceInfoSnapshot();
        }

        ServiceInfoSnapshot serviceInfoSnapshot = this.getCloudServiceManager().getServiceInfoSnapshot(uniqueId);
        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.addServiceRemoteInclusionToCloudService(uniqueId, serviceRemoteInclusion);

        return null;
    }

    @Override
    public ServiceInfoSnapshot addServiceDeploymentToCloudService(UUID uniqueId, ServiceDeployment serviceDeployment)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceDeployment);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return null;

        ICloudService cloudService = cloudServiceManager.getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.getDeployments().add(serviceDeployment);
            return cloudService.getServiceInfoSnapshot();
        }

        ServiceInfoSnapshot serviceInfoSnapshot = this.getCloudServiceManager().getServiceInfoSnapshot(uniqueId);
        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.addServiceDeploymentToCloudService(uniqueId, serviceDeployment);

        return null;
    }

    @Override
    public Queue<String> getCachedLogMessagesFromService(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return null;

        ICloudService cloudService = cloudServiceManager.getCloudService(uniqueId);

        if (cloudService != null) return cloudService.getServiceConsoleLogCache().getCachedLogMessages();

        ServiceInfoSnapshot serviceInfoSnapshot = this.getCloudServiceManager().getServiceInfoSnapshot(uniqueId);
        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            return clusterNodeServer.getCachedLogMessagesFromService(uniqueId);

        return null;
    }

    @Override
    public void setCloudServiceLifeCycle(ServiceInfoSnapshot serviceInfoSnapshot, ServiceLifeCycle lifeCycle)
    {
        Validate.checkNotNull(serviceInfoSnapshot);
        Validate.checkNotNull(lifeCycle);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(serviceInfoSnapshot.getServiceId().getUniqueId()))
            return;

        ICloudService cloudService = this.cloudServiceManager.getCloudService(serviceInfoSnapshot.getServiceId().getUniqueId());
        if (cloudService != null)
        {
            switch (lifeCycle)
            {
                case RUNNING:
                    try
                    {
                        cloudService.start();
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    break;
                case STOPPED:
                    scheduleTask(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception
                        {
                            cloudService.stop();
                            return null;
                        }
                    });
                    break;
                case DELETED:
                    scheduleTask(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception
                        {
                            cloudService.delete();
                            return null;
                        }
                    });
                    break;
            }
        } else
        {
            IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

            if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
                clusterNodeServer.setCloudServiceLifeCycle(serviceInfoSnapshot, lifeCycle);
        }
    }

    @Override
    public void restartCloudService(ServiceInfoSnapshot serviceInfoSnapshot)
    {
        Validate.checkNotNull(serviceInfoSnapshot);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(serviceInfoSnapshot.getServiceId().getUniqueId()))
            return;

        ICloudService cloudService = this.getCloudServiceManager().getCloudService(serviceInfoSnapshot.getServiceId().getUniqueId());

        if (cloudService != null)
        {
            try
            {
                cloudService.restart();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            return;
        }

        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            clusterNodeServer.restartCloudService(serviceInfoSnapshot);
    }

    @Override
    public void killCloudService(ServiceInfoSnapshot serviceInfoSnapshot)
    {
        Validate.checkNotNull(serviceInfoSnapshot);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(serviceInfoSnapshot.getServiceId().getUniqueId()))
            return;

        ICloudService cloudService = this.getCloudServiceManager().getCloudService(serviceInfoSnapshot.getServiceId().getUniqueId());

        if (cloudService != null)
        {
            try
            {
                cloudService.kill();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            return;
        }

        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            clusterNodeServer.killCloudService(serviceInfoSnapshot);
    }

    @Override
    public void runCommand(ServiceInfoSnapshot serviceInfoSnapshot, String command)
    {
        Validate.checkNotNull(serviceInfoSnapshot);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(serviceInfoSnapshot.getServiceId().getUniqueId()))
            return;

        ICloudService cloudService = this.getCloudServiceManager().getCloudService(serviceInfoSnapshot.getServiceId().getUniqueId());

        if (cloudService != null)
        {
            try
            {
                cloudService.runCommand(command);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            return;
        }

        IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

        if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
            clusterNodeServer.runCommand(serviceInfoSnapshot, command);
    }

    @Override
    public Collection<UUID> getServicesAsUniqueId()
    {
        return Collections.unmodifiableCollection(this.cloudServiceManager.getGlobalServiceInfoSnapshots().keySet());
    }

    @Override
    public ServiceInfoSnapshot getCloudServiceByName(String name)
    {
        return Iterables.first(cloudServiceManager.getCloudServices().values(), new Predicate<ICloudService>() {
            @Override
            public boolean test(ICloudService cloudService)
            {
                return cloudService.getServiceId().getName().equalsIgnoreCase(name);
            }
        }).getServiceInfoSnapshot();
    }

    @Override
    public Collection<ServiceInfoSnapshot> getCloudServices()
    {
        return this.cloudServiceManager.getServiceInfoSnapshots();
    }

    @Override
    public Collection<ServiceInfoSnapshot> getStartedCloudServices()
    {
        return Iterables.filter(this.getCloudServices(), new Predicate<ServiceInfoSnapshot>() {
            @Override
            public boolean test(ServiceInfoSnapshot serviceInfoSnapshot)
            {
                return serviceInfoSnapshot.getLifeCycle() == ServiceLifeCycle.RUNNING;
            }
        });
    }

    @Override
    public Collection<ServiceInfoSnapshot> getCloudService(String taskName)
    {
        Validate.checkNotNull(taskName);

        return this.cloudServiceManager.getServiceInfoSnapshots(taskName);
    }

    @Override
    public Collection<ServiceInfoSnapshot> getCloudServiceByGroup(String group)
    {
        Validate.checkNotNull(group);

        return Iterables.filter(this.cloudServiceManager.getGlobalServiceInfoSnapshots().values(), new Predicate<ServiceInfoSnapshot>() {
            @Override
            public boolean test(ServiceInfoSnapshot serviceInfoSnapshot)
            {
                return Iterables.contains(group, serviceInfoSnapshot.getConfiguration().getGroups());
            }
        });
    }

    @Override
    public ServiceInfoSnapshot getCloudService(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return this.cloudServiceManager.getServiceInfoSnapshot(uniqueId);
    }

    @Override
    public Integer getServicesCount()
    {
        return this.getCloudServiceManager().getGlobalServiceInfoSnapshots().size();
    }

    @Override
    public Integer getServicesCountByGroup(String group)
    {
        Validate.checkNotNull(group);

        int amount = 0;

        for (ServiceInfoSnapshot serviceInfoSnapshot : this.getCloudServiceManager().getGlobalServiceInfoSnapshots().values())
            if (Iterables.contains(group, serviceInfoSnapshot.getConfiguration().getGroups()))
                amount++;

        return amount;
    }

    @Override
    public Integer getServicesCountByTask(String taskName)
    {
        Validate.checkNotNull(taskName);

        int amount = 0;

        for (ServiceInfoSnapshot serviceInfoSnapshot : this.getCloudServiceManager().getGlobalServiceInfoSnapshots().values())
            if (serviceInfoSnapshot.getServiceId().getTaskName().equals(taskName))
                amount++;

        return amount;
    }

    @Override
    public Collection<ServiceTask> getPermanentServiceTasks()
    {
        return this.cloudServiceManager.getServiceTasks();
    }

    @Override
    public ServiceTask getServiceTask(String name)
    {
        Validate.checkNotNull(name);

        return this.cloudServiceManager.getServiceTask(name);
    }

    @Override
    public boolean isServiceTaskPresent(String name)
    {
        Validate.checkNotNull(name);

        return this.cloudServiceManager.isTaskPresent(name);
    }

    @Override
    public void addPermanentServiceTask(ServiceTask serviceTask)
    {
        Validate.checkNotNull(serviceTask);

        this.cloudServiceManager.addPermanentServiceTask(serviceTask);
    }

    @Override
    public void removePermanentServiceTask(String name)
    {
        Validate.checkNotNull(name);

        this.cloudServiceManager.removePermanentServiceTask(name);
    }

    @Override
    public void removePermanentServiceTask(ServiceTask serviceTask)
    {
        Validate.checkNotNull(serviceTask);

        this.cloudServiceManager.removePermanentServiceTask(serviceTask);
    }

    @Override
    public Collection<GroupConfiguration> getGroupConfigurations()
    {
        return this.cloudServiceManager.getGroupConfigurations();
    }

    @Override
    public GroupConfiguration getGroupConfiguration(String name)
    {
        Validate.checkNotNull(name);

        return this.cloudServiceManager.getGroupConfiguration(name);
    }

    @Override
    public boolean isGroupConfigurationPresent(String name)
    {
        Validate.checkNotNull(name);

        return this.cloudServiceManager.isGroupConfigurationPresent(name);
    }

    @Override
    public void addGroupConfiguration(GroupConfiguration groupConfiguration)
    {
        Validate.checkNotNull(groupConfiguration);

        this.cloudServiceManager.addGroupConfiguration(groupConfiguration);
    }

    @Override
    public void removeGroupConfiguration(String name)
    {
        Validate.checkNotNull(name);

        this.cloudServiceManager.removeGroupConfiguration(name);
    }

    @Override
    public void removeGroupConfiguration(GroupConfiguration groupConfiguration)
    {
        Validate.checkNotNull(groupConfiguration);

        this.cloudServiceManager.removeGroupConfiguration(groupConfiguration);
    }

    @Override
    public NetworkClusterNode[] getNodes()
    {
        return this.config.getClusterConfig().getNodes().toArray(new NetworkClusterNode[0]);
    }

    @Override
    public NetworkClusterNode getNode(String uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return Iterables.first(this.config.getClusterConfig().getNodes(), new Predicate<NetworkClusterNode>() {
            @Override
            public boolean test(NetworkClusterNode networkClusterNode)
            {
                return networkClusterNode.getUniqueId().equals(uniqueId);
            }
        });
    }

    @Override
    public NetworkClusterNodeInfoSnapshot[] getNodeInfoSnapshots()
    {
        Collection<NetworkClusterNodeInfoSnapshot> nodeInfoSnapshots = Iterables.newArrayList();

        for (IClusterNodeServer clusterNodeServer : this.clusterNodeServerProvider.getNodeServers())
            if (clusterNodeServer.isConnected() && clusterNodeServer.getNodeInfoSnapshot() != null)
                nodeInfoSnapshots.add(clusterNodeServer.getNodeInfoSnapshot());

        return nodeInfoSnapshots.toArray(new NetworkClusterNodeInfoSnapshot[0]);
    }

    @Override
    public NetworkClusterNodeInfoSnapshot getNodeInfoSnapshot(String uniqueId)
    {
        for (IClusterNodeServer clusterNodeServer : this.clusterNodeServerProvider.getNodeServers())
            if (clusterNodeServer.getNodeInfo().getUniqueId().equals(uniqueId) && clusterNodeServer.isConnected() && clusterNodeServer.getNodeInfoSnapshot() != null)
                return clusterNodeServer.getNodeInfoSnapshot();

        return null;
    }

    @Override
    public Collection<ServiceTemplate> getLocalTemplateStorageTemplates()
    {
        return this.getServicesRegistry().getService(ITemplateStorage.class, LocalTemplateStorage.LOCAL_TEMPLATE_STORAGE).getTemplates();
    }

    @Override
    public Collection<ServiceInfoSnapshot> getCloudServices(ServiceEnvironmentType environment)
    {
        Validate.checkNotNull(environment);

        return cloudServiceManager.getServiceInfoSnapshots(new Predicate<ServiceInfoSnapshot>() {
            @Override
            public boolean test(ServiceInfoSnapshot serviceInfoSnapshot)
            {
                return serviceInfoSnapshot.getServiceId().getEnvironment() == environment;
            }
        });
    }

    @Override
    public Collection<ServiceTemplate> getTemplateStorageTemplates(String serviceName)
    {
        Validate.checkNotNull(serviceName);

        Collection<ServiceTemplate> collection = Iterables.newArrayList();

        if (servicesRegistry.containsService(ITemplateStorage.class, serviceName))
            collection.addAll(servicesRegistry.getService(ITemplateStorage.class, serviceName).getTemplates());

        return collection;
    }

    @Override
    public Pair<Boolean, String[]> sendCommandLineAsPermissionUser(UUID uniqueId, String commandLine)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(commandLine);

        IPermissionUser permissionUser = permissionManagement.getUser(uniqueId);
        if (permissionUser != null)
        {
            IPermissionUserCommandSender commandSender = new DefaultPermissionUserCommandSender(permissionUser, permissionManagement);
            boolean value = commandMap.dispatchCommand(commandSender, commandLine);

            return new Pair<>(value, commandSender.getWrittenMessages().toArray(new String[0]));
        } else
            return new Pair<>(false, new String[0]);
    }

    @Override
    public void addUser(IPermissionUser permissionUser)
    {
        Validate.checkNotNull(permissionUser);

        getPermissionManagement().addUser(permissionUser);
    }

    @Override
    public void updateUser(IPermissionUser permissionUser)
    {
        Validate.checkNotNull(permissionUser);

        getPermissionManagement().updateUser(permissionUser);
    }

    @Override
    public void deleteUser(String name)
    {
        Validate.checkNotNull(name);

        getPermissionManagement().deleteUser(name);
    }

    @Override
    public void deleteUser(IPermissionUser permissionUser)
    {
        Validate.checkNotNull(permissionUser);

        getPermissionManagement().deleteUser(permissionUser);
    }

    @Override
    public boolean containsUser(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return getPermissionManagement().containsUser(uniqueId);
    }

    @Override
    public boolean containsUser(String name)
    {
        Validate.checkNotNull(name);

        return getPermissionManagement().containsUser(name);
    }

    @Override
    public IPermissionUser getUser(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return getPermissionManagement().getUser(uniqueId);
    }

    @Override
    public List<IPermissionUser> getUser(String name)
    {
        Validate.checkNotNull(name);

        return getPermissionManagement().getUser(name);
    }

    @Override
    public Collection<IPermissionUser> getUsers()
    {
        return getPermissionManagement().getUsers();
    }

    @Override
    public void setUsers(Collection<? extends IPermissionUser> users)
    {
        Validate.checkNotNull(users);

        getPermissionManagement().setUsers(users);
    }

    @Override
    public Collection<IPermissionUser> getUserByGroup(String group)
    {
        Validate.checkNotNull(group);

        return getPermissionManagement().getUserByGroup(group);
    }

    @Override
    public void addGroup(IPermissionGroup permissionGroup)
    {
        Validate.checkNotNull(permissionGroup);

        getPermissionManagement().addGroup(permissionGroup);
    }

    @Override
    public void updateGroup(IPermissionGroup permissionGroup)
    {
        Validate.checkNotNull(permissionGroup);

        getPermissionManagement().updateGroup(permissionGroup);
    }

    @Override
    public void deleteGroup(String group)
    {
        Validate.checkNotNull(group);

        getPermissionManagement().deleteGroup(group);
    }

    @Override
    public void deleteGroup(IPermissionGroup group)
    {
        Validate.checkNotNull(group);

        getPermissionManagement().deleteGroup(group);
    }

    @Override
    public boolean containsGroup(String group)
    {
        Validate.checkNotNull(group);

        return getPermissionManagement().containsGroup(group);
    }

    @Override
    public IPermissionGroup getGroup(String name)
    {
        Validate.checkNotNull(name);

        return getPermissionManagement().getGroup(name);
    }

    @Override
    public Collection<IPermissionGroup> getGroups()
    {
        return getPermissionManagement().getGroups();
    }

    @Override
    public void setGroups(Collection<? extends IPermissionGroup> groups)
    {
        Validate.checkNotNull(groups);

        getPermissionManagement().setGroups(groups);
    }

    @Override
    public ITask<String[]> sendCommandLineAsync(String commandLine)
    {
        return scheduleTask(new Callable<String[]>() {
            @Override
            public String[] call() throws Exception
            {
                return CloudNet.this.sendCommandLine(commandLine);
            }
        });
    }

    @Override
    public ITask<String[]> sendCommandLineAsync(String nodeUniqueId, String commandLine)
    {
        return scheduleTask(new Callable<String[]>() {
            @Override
            public String[] call() throws Exception
            {
                return CloudNet.this.sendCommandLine(nodeUniqueId, commandLine);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> createCloudServiceAsync(ServiceTask serviceTask)
    {
        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.createCloudService(serviceTask);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> createCloudServiceAsync(ServiceConfiguration serviceConfiguration)
    {
        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.createCloudService(serviceConfiguration);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> createCloudServiceAsync(String name, String runtime, boolean autoDeleteOnStop, boolean staticService,
                                                              Collection<ServiceRemoteInclusion> includes,
                                                              Collection<ServiceTemplate> templates, Collection<ServiceDeployment> deployments,
                                                              Collection<String> groups, ProcessConfiguration processConfiguration, Integer port)
    {
        Validate.checkNotNull(name);
        Validate.checkNotNull(includes);
        Validate.checkNotNull(templates);
        Validate.checkNotNull(deployments);
        Validate.checkNotNull(groups);
        Validate.checkNotNull(processConfiguration);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.createCloudService(name, runtime, autoDeleteOnStop, staticService, includes, templates, deployments, groups, processConfiguration, port);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> createCloudServiceAsync(
        String nodeUniqueId, int amount, String name, String runtime, boolean autoDeleteOnStop, boolean staticService,
        Collection<ServiceRemoteInclusion> includes,
        Collection<ServiceTemplate> templates, Collection<ServiceDeployment> deployments, Collection<String> groups, ProcessConfiguration processConfiguration, Integer port)
    {
        Validate.checkNotNull(nodeUniqueId);
        Validate.checkNotNull(name);
        Validate.checkNotNull(includes);
        Validate.checkNotNull(templates);
        Validate.checkNotNull(deployments);
        Validate.checkNotNull(groups);
        Validate.checkNotNull(processConfiguration);

        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.createCloudService(nodeUniqueId, amount, name, runtime, autoDeleteOnStop, staticService, includes, templates, deployments, groups, processConfiguration, port);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> sendCommandLineToCloudServiceAsync(UUID uniqueId, String commandLine)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(commandLine);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.sendCommandLineToCloudService(uniqueId, commandLine);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> addServiceTemplateToCloudServiceAsync(UUID uniqueId, ServiceTemplate serviceTemplate)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceTemplate);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.addServiceTemplateToCloudService(uniqueId, serviceTemplate);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> addServiceRemoteInclusionToCloudServiceAsync(UUID uniqueId, ServiceRemoteInclusion serviceRemoteInclusion)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceRemoteInclusion);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.addServiceRemoteInclusionToCloudService(uniqueId, serviceRemoteInclusion);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> addServiceDeploymentToCloudServiceAsync(UUID uniqueId, ServiceDeployment serviceDeployment)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(serviceDeployment);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.addServiceDeploymentToCloudService(uniqueId, serviceDeployment);
            }
        });
    }

    @Override
    public ITask<Queue<String>> getCachedLogMessagesFromServiceAsync(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<Queue<String>>() {
            @Override
            public Queue<String> call() throws Exception
            {
                return CloudNet.this.getCachedLogMessagesFromService(uniqueId);
            }
        });
    }

    @Override
    public void includeWaitingServiceTemplates(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return;

        ICloudService cloudService = getCloudServiceManager().getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.includeTemplates();
            return;
        }

        ServiceInfoSnapshot serviceInfoSnapshot = getCloudServiceManager().getGlobalServiceInfoSnapshots().get(uniqueId);

        if (serviceInfoSnapshot != null)
        {
            IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

            if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
                clusterNodeServer.includeWaitingServiceTemplates(uniqueId);
        }
    }

    @Override
    public void includeWaitingServiceInclusions(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return;

        ICloudService cloudService = getCloudServiceManager().getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.includeInclusions();
            return;
        }

        ServiceInfoSnapshot serviceInfoSnapshot = getCloudServiceManager().getGlobalServiceInfoSnapshots().get(uniqueId);

        if (serviceInfoSnapshot != null)
        {
            IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

            if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
                clusterNodeServer.includeWaitingServiceInclusions(uniqueId);
        }
    }

    @Override
    public void deployResources(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        if (!getCloudServiceManager().getGlobalServiceInfoSnapshots().containsKey(uniqueId))
            return;

        ICloudService cloudService = getCloudServiceManager().getCloudService(uniqueId);

        if (cloudService != null)
        {
            cloudService.deployResources();
            return;
        }

        ServiceInfoSnapshot serviceInfoSnapshot = getCloudServiceManager().getGlobalServiceInfoSnapshots().get(uniqueId);

        if (serviceInfoSnapshot != null)
        {
            IClusterNodeServer clusterNodeServer = this.clusterNodeServerProvider.getNodeServer(serviceInfoSnapshot.getServiceId().getNodeUniqueId());

            if (clusterNodeServer != null && clusterNodeServer.isConnected() && clusterNodeServer.getChannel() != null)
                clusterNodeServer.deployResources(uniqueId);
        }
    }

    @Override
    public ITask<Collection<UUID>> getServicesAsUniqueIdAsync()
    {
        return scheduleTask(new Callable<Collection<UUID>>() {
            @Override
            public Collection<UUID> call() throws Exception
            {
                return CloudNet.this.getServicesAsUniqueId();
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> getCloudServiceByNameAsync(String name)
    {
        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.getCloudServiceByName(name);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> getCloudServicesAsync()
    {
        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.getCloudServices();
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> getStartedCloudServiceInfoSnapshotsAsync()
    {
        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.getStartedCloudServices();
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> getCloudServicesAsync(String taskName)
    {
        Validate.checkNotNull(taskName);

        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.getCloudService(taskName);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> getCloudServicesByGroupAsync(String group)
    {
        Validate.checkNotNull(group);

        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.getCloudServiceByGroup(group);
            }
        });
    }

    @Override
    public ITask<Integer> getServicesCountAsync()
    {
        return scheduleTask(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                return CloudNet.this.getServicesCount();
            }
        });
    }

    @Override
    public ITask<Integer> getServicesCountByGroupAsync(String group)
    {
        Validate.checkNotNull(group);

        return scheduleTask(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                return CloudNet.this.getServicesCountByGroup(group);
            }
        });
    }

    @Override
    public ITask<Integer> getServicesCountByTaskAsync(String taskName)
    {
        Validate.checkNotNull(taskName);

        return scheduleTask(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                return CloudNet.this.getServicesCountByTask(taskName);
            }
        });
    }

    @Override
    public ITask<ServiceInfoSnapshot> getCloudServicesAsync(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<ServiceInfoSnapshot>() {
            @Override
            public ServiceInfoSnapshot call() throws Exception
            {
                return CloudNet.this.getCloudService(uniqueId);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceTask>> getPermanentServiceTasksAsync()
    {
        return scheduleTask(new Callable<Collection<ServiceTask>>() {
            @Override
            public Collection<ServiceTask> call() throws Exception
            {
                return CloudNet.this.getPermanentServiceTasks();
            }
        });
    }

    @Override
    public ITask<ServiceTask> getServiceTaskAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<ServiceTask>() {
            @Override
            public ServiceTask call() throws Exception
            {
                return CloudNet.this.getServiceTask(name);
            }
        });
    }

    @Override
    public ITask<Boolean> isServiceTaskPresentAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception
            {
                return CloudNet.this.isServiceTaskPresent(name);
            }
        });
    }

    @Override
    public ITask<Collection<GroupConfiguration>> getGroupConfigurationsAsync()
    {
        return scheduleTask(new Callable<Collection<GroupConfiguration>>() {
            @Override
            public Collection<GroupConfiguration> call() throws Exception
            {
                return CloudNet.this.getGroupConfigurations();
            }
        });
    }

    @Override
    public ITask<GroupConfiguration> getGroupConfigurationAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<GroupConfiguration>() {
            @Override
            public GroupConfiguration call() throws Exception
            {
                return CloudNet.this.getGroupConfiguration(name);
            }
        });
    }

    @Override
    public ITask<Boolean> isGroupConfigurationPresentAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception
            {
                return CloudNet.this.isGroupConfigurationPresent(name);
            }
        });
    }

    @Override
    public ITask<NetworkClusterNode[]> getNodesAsync()
    {
        return scheduleTask(new Callable<NetworkClusterNode[]>() {
            @Override
            public NetworkClusterNode[] call() throws Exception
            {
                return CloudNet.this.getNodes();
            }
        });
    }

    @Override
    public ITask<NetworkClusterNode> getNodeAsync(String uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<NetworkClusterNode>() {
            @Override
            public NetworkClusterNode call() throws Exception
            {
                return CloudNet.this.getNode(uniqueId);
            }
        });
    }

    @Override
    public ITask<NetworkClusterNodeInfoSnapshot[]> getNodeInfoSnapshotsAsync()
    {
        return scheduleTask(new Callable<NetworkClusterNodeInfoSnapshot[]>() {
            @Override
            public NetworkClusterNodeInfoSnapshot[] call() throws Exception
            {
                return CloudNet.this.getNodeInfoSnapshots();
            }
        });
    }

    @Override
    public ITask<NetworkClusterNodeInfoSnapshot> getNodeInfoSnapshotAsync(String uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<NetworkClusterNodeInfoSnapshot>() {
            @Override
            public NetworkClusterNodeInfoSnapshot call() throws Exception
            {
                return CloudNet.this.getNodeInfoSnapshot(uniqueId);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceTemplate>> getLocalTemplateStorageTemplatesAsync()
    {
        return scheduleTask(new Callable<Collection<ServiceTemplate>>() {
            @Override
            public Collection<ServiceTemplate> call() throws Exception
            {
                return CloudNet.this.getLocalTemplateStorageTemplates();
            }
        });
    }

    @Override
    public ITask<Collection<ServiceInfoSnapshot>> getCloudServicesAsync(ServiceEnvironmentType environment)
    {
        Validate.checkNotNull(environment);

        return scheduleTask(new Callable<Collection<ServiceInfoSnapshot>>() {
            @Override
            public Collection<ServiceInfoSnapshot> call() throws Exception
            {
                return CloudNet.this.getCloudServices(environment);
            }
        });
    }

    @Override
    public ITask<Collection<ServiceTemplate>> getTemplateStorageTemplatesAsync(String serviceName)
    {
        Validate.checkNotNull(serviceName);

        return scheduleTask(new Callable<Collection<ServiceTemplate>>() {
            @Override
            public Collection<ServiceTemplate> call() throws Exception
            {
                return CloudNet.this.getTemplateStorageTemplates(serviceName);
            }
        });
    }

    @Override
    public ITask<Pair<Boolean, String[]>> sendCommandLineAsPermissionUserAsync(UUID uniqueId, String commandLine)
    {
        Validate.checkNotNull(uniqueId);
        Validate.checkNotNull(commandLine);

        return scheduleTask(new Callable<Pair<Boolean, String[]>>() {
            @Override
            public Pair<Boolean, String[]> call() throws Exception
            {
                return CloudNet.this.sendCommandLineAsPermissionUser(uniqueId, commandLine);
            }
        });
    }

    @Override
    public ITask<Void> addUserAsync(IPermissionUser permissionUser)
    {
        Validate.checkNotNull(permissionUser);

        return scheduleTask(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                return null;
            }
        });
    }

    @Override
    public ITask<Boolean> containsUserAsync(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception
            {
                return CloudNet.this.containsUser(uniqueId);
            }
        });
    }

    @Override
    public ITask<Boolean> containsUserAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception
            {
                return CloudNet.this.containsUser(name);
            }
        });
    }

    @Override
    public ITask<IPermissionUser> getUserAsync(UUID uniqueId)
    {
        Validate.checkNotNull(uniqueId);

        return scheduleTask(new Callable<IPermissionUser>() {
            @Override
            public IPermissionUser call() throws Exception
            {
                return CloudNet.this.getUser(uniqueId);
            }
        });
    }

    @Override
    public ITask<List<IPermissionUser>> getUserAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<List<IPermissionUser>>() {
            @Override
            public List<IPermissionUser> call() throws Exception
            {
                return CloudNet.this.getUser(name);
            }
        });
    }

    @Override
    public ITask<Collection<IPermissionUser>> getUsersAsync()
    {
        return scheduleTask(new Callable<Collection<IPermissionUser>>() {
            @Override
            public Collection<IPermissionUser> call() throws Exception
            {
                return CloudNet.this.getUsers();
            }
        });
    }

    @Override
    public ITask<Collection<IPermissionUser>> getUserByGroupAsync(String group)
    {
        Validate.checkNotNull(group);

        return scheduleTask(new Callable<Collection<IPermissionUser>>() {
            @Override
            public Collection<IPermissionUser> call() throws Exception
            {
                return CloudNet.this.getUserByGroup(group);
            }
        });
    }

    @Override
    public ITask<Boolean> containsGroupAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception
            {
                return CloudNet.this.containsGroup(name);
            }
        });
    }

    @Override
    public ITask<IPermissionGroup> getGroupAsync(String name)
    {
        Validate.checkNotNull(name);

        return scheduleTask(new Callable<IPermissionGroup>() {
            @Override
            public IPermissionGroup call() throws Exception
            {
                return CloudNet.this.getGroup(name);
            }
        });
    }

    @Override
    public ITask<Collection<IPermissionGroup>> getGroupsAsync()
    {
        return scheduleTask(new Callable<Collection<IPermissionGroup>>() {
            @Override
            public Collection<IPermissionGroup> call() throws Exception
            {
                return CloudNet.this.getGroups();
            }
        });
    }

    public <T> ITask<T> runTask(Callable<T> runnable)
    {
        ITask<T> task = new ListenableTask<>(runnable);

        this.processQueue.offer(task);
        return task;
    }

    public ITask<?> runTask(Runnable runnable)
    {
        return this.runTask(Executors.callable(runnable));
    }

    public boolean isMainThread()
    {
        return Thread.currentThread().getName().equals("Application-Thread");
    }

    public void deployTemplateInCluster(ServiceTemplate serviceTemplate, byte[] resource)
    {
        Validate.checkNotNull(serviceTemplate);
        Validate.checkNotNull(resource);

        this.getClusterNodeServerProvider().deployTemplateInCluster(serviceTemplate, resource);
    }

    public void updateServiceTasksInCluster()
    {
        this.getClusterNodeServerProvider().sendPacket(new PacketServerSetServiceTaskList(this.getCloudServiceManager().getServiceTasks()));
    }

    public void updateGroupConfigurationsInCluster()
    {
        this.getClusterNodeServerProvider().sendPacket(new PacketServerSetGroupConfigurationList(this.getCloudServiceManager().getGroupConfigurations()));
    }

    public ITask<Void> sendAllAsync(IPacket packet)
    {
        return scheduleTask(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                sendAll(packet);
                return null;
            }
        });
    }

    public void sendAll(IPacket packet)
    {
        Validate.checkNotNull(packet);

        for (IClusterNodeServer clusterNodeServer : getClusterNodeServerProvider().getNodeServers())
            clusterNodeServer.saveSendPacket(packet);

        for (ICloudService cloudService : getCloudServiceManager().getCloudServices().values())
            if (cloudService.getNetworkChannel() != null)
                cloudService.getNetworkChannel().sendPacket(packet);
    }

    public ITask<Void> sendAllAsync(IPacket... packets)
    {
        return scheduleTask(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                sendAll(packets);
                return null;
            }
        });
    }

    public void sendAll(IPacket... packets)
    {
        Validate.checkNotNull(packets);

        for (IClusterNodeServer clusterNodeServer : getClusterNodeServerProvider().getNodeServers())
            for (IPacket packet : packets)
                if (packet != null)
                    clusterNodeServer.saveSendPacket(packet);

        for (ICloudService cloudService : getCloudServiceManager().getCloudServices().values())
            if (cloudService.getNetworkChannel() != null)
                cloudService.getNetworkChannel().sendPacket(packets);
    }

    public NetworkClusterNodeInfoSnapshot createClusterNodeInfoSnapshot()
    {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        return new NetworkClusterNodeInfoSnapshot(
            System.currentTimeMillis(),
            this.config.getIdentity(),
            CloudNet.class.getPackage().getImplementationVersion(),
            this.cloudServiceManager.getCloudServices().size(),
            this.cloudServiceManager.getCurrentUsedHeapMemory(),
            this.cloudServiceManager.getCurrentReservedMemory(),
            this.config.getMaxMemory(),
            new ProcessSnapshot(
                memoryMXBean.getHeapMemoryUsage().getUsed(),
                memoryMXBean.getNonHeapMemoryUsage().getUsed(),
                memoryMXBean.getHeapMemoryUsage().getMax(),
                ManagementFactory.getClassLoadingMXBean().getLoadedClassCount(),
                ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount(),
                ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount(),
                Iterables.map(Thread.getAllStackTraces().keySet(), new Function<Thread, ThreadSnapshot>() {
                    @Override
                    public ThreadSnapshot apply(Thread thread)
                    {
                        return new ThreadSnapshot(thread.getId(), thread.getName(), thread.getState(), thread.isDaemon(), thread.getPriority());
                    }
                }),
                CPUUsageResolver.getProcessCPUUsage()
            ),
            Iterables.map(this.moduleProvider.getModules(), new Function<IModuleWrapper, NetworkClusterNodeExtensionSnapshot>() {
                @Override
                public NetworkClusterNodeExtensionSnapshot apply(IModuleWrapper moduleWrapper)
                {
                    return new NetworkClusterNodeExtensionSnapshot(
                        moduleWrapper.getModuleConfiguration().getGroup(),
                        moduleWrapper.getModuleConfiguration().getName(),
                        moduleWrapper.getModuleConfiguration().getVersion(),
                        moduleWrapper.getModuleConfiguration().getAuthor(),
                        moduleWrapper.getModuleConfiguration().getWebsite(),
                        moduleWrapper.getModuleConfiguration().getDescription()
                    );
                }
            })
        );
    }

    public Collection<IClusterNodeServer> getValidClusterNodeServers(ServiceTask serviceTask)
    {
        return Iterables.filter(clusterNodeServerProvider.getNodeServers(), new Predicate<IClusterNodeServer>() {
            @Override
            public boolean test(IClusterNodeServer clusterNodeServer)
            {
                return clusterNodeServer.isConnected() && clusterNodeServer.getNodeInfoSnapshot() != null && (
                    (!serviceTask.getAssociatedNodes().isEmpty() && serviceTask.getAssociatedNodes().contains(clusterNodeServer.getNodeInfo().getUniqueId())) ||
                        serviceTask.getAssociatedNodes().isEmpty()
                );
            }
        });
    }

    public NetworkClusterNodeInfoSnapshot searchLogicNode(ServiceTask serviceTask)
    {
        Validate.checkNotNull(serviceTask);

        Collection<IClusterNodeServer> clusterNodeServers = this.getValidClusterNodeServers(serviceTask);
        NetworkClusterNodeInfoSnapshot networkClusterNodeInfoSnapshot = this.currentNetworkClusterNodeInfoSnapshot;

        for (IClusterNodeServer node : clusterNodeServers)
        {
            if (node.getNodeInfoSnapshot() != null &&
                (node.getNodeInfoSnapshot().getMaxMemory() - node.getNodeInfoSnapshot().getReservedMemory())
                    > (networkClusterNodeInfoSnapshot.getMaxMemory() - networkClusterNodeInfoSnapshot.getReservedMemory()) &&
                //
                (node.getNodeInfoSnapshot().getProcessSnapshot().getCpuUsage() * node.getNodeInfoSnapshot().getCurrentServicesCount())
                    < (networkClusterNodeInfoSnapshot.getProcessSnapshot().getCpuUsage() *
                    networkClusterNodeInfoSnapshot.getCurrentServicesCount())
                )
                networkClusterNodeInfoSnapshot = node.getNodeInfoSnapshot();
        }

        return networkClusterNodeInfoSnapshot;
    }

    public boolean competeWithCluster(ServiceTask serviceTask)
    {
        Collection<IClusterNodeServer> clusterNodeServers = this.getValidClusterNodeServers(serviceTask);

        boolean allow = true;

        for (IClusterNodeServer clusterNodeServer : clusterNodeServers)
            if (
                clusterNodeServer.getNodeInfoSnapshot() != null &&
                    (clusterNodeServer.getNodeInfoSnapshot().getMaxMemory() - clusterNodeServer.getNodeInfoSnapshot().getReservedMemory())
                        > (this.currentNetworkClusterNodeInfoSnapshot.getMaxMemory() - this.currentNetworkClusterNodeInfoSnapshot.getReservedMemory()) &&
                    (clusterNodeServer.getNodeInfoSnapshot().getProcessSnapshot().getCpuUsage() * clusterNodeServer.getNodeInfoSnapshot().getCurrentServicesCount())
                        < (this.currentNetworkClusterNodeInfoSnapshot.getProcessSnapshot().getCpuUsage() *
                        this.currentNetworkClusterNodeInfoSnapshot.getCurrentServicesCount())
                )
                allow = false;

        return clusterNodeServers.size() == 0 || allow;
    }

    public void unregisterPacketListenersByClassLoader(ClassLoader classLoader)
    {
        Validate.checkNotNull(classLoader);

        networkClient.getPacketRegistry().removeListeners(classLoader);
        networkServer.getPacketRegistry().removeListeners(classLoader);

        for (INetworkChannel channel : networkServer.getChannels())
            channel.getPacketRegistry().removeListeners(classLoader);

        for (INetworkChannel channel : networkClient.getChannels())
            channel.getPacketRegistry().removeListeners(classLoader);
    }

    public void publishNetworkClusterNodeInfoSnapshotUpdate()
    {
        this.lastNetworkClusterNodeInfoSnapshot = this.currentNetworkClusterNodeInfoSnapshot;
        this.currentNetworkClusterNodeInfoSnapshot = this.createClusterNodeInfoSnapshot();

        this.getEventManager().callEvent(new NetworkClusterNodeInfoConfigureEvent(currentNetworkClusterNodeInfoSnapshot));
        this.sendAll(new PacketServerClusterNodeInfoUpdate(this.currentNetworkClusterNodeInfoSnapshot));
    }

    public void publishUpdateJsonPermissionManagement()
    {
        if (permissionManagement instanceof DefaultJsonFilePermissionManagement)
            clusterNodeServerProvider.sendPacket(new PacketServerSetJsonFilePermissions(
                permissionManagement.getUsers(),
                permissionManagement.getGroups()
            ));

        if (permissionManagement instanceof DefaultDatabasePermissionManagement)
            clusterNodeServerProvider.sendPacket(new PacketServerSetDatabaseGroupFilePermissions(
                permissionManagement.getGroups()
            ));
    }

    public void publishH2DatabaseDataToCluster(INetworkChannel channel)
    {
        if (channel != null)
            if (databaseProvider instanceof H2DatabaseProvider)
            {
                Map<String, Map<String, JsonDocument>> map = allocateDatabaseData();

                channel.sendPacket(new PacketServerSetH2DatabaseData(map));

                for (Map.Entry<String, Map<String, JsonDocument>> entry : map.entrySet())
                    entry.getValue().clear();

                map.clear();
            }
    }

    public void publishH2DatabaseDataToCluster()
    {
        if (databaseProvider instanceof H2DatabaseProvider)
        {
            Map<String, Map<String, JsonDocument>> map = allocateDatabaseData();

            clusterNodeServerProvider.sendPacket(new PacketServerSetH2DatabaseData(map));

            for (Map.Entry<String, Map<String, JsonDocument>> entry : map.entrySet())
                entry.getValue().clear();

            map.clear();
        }
    }

    private Map<String, Map<String, JsonDocument>> allocateDatabaseData()
    {
        Map<String, Map<String, JsonDocument>> map = Maps.newHashMap();

        for (String name : databaseProvider.getDatabaseNames())
        {
            if (!map.containsKey(name)) map.put(name, Maps.newHashMap());
            IDatabase database = databaseProvider.getDatabase(name);
            map.get(name).putAll(database.entries());
        }

        return map;
    }

    /*= -------------------------------------------------------------------------------------------- =*/
    //private methods
    /*= -------------------------------------------------------------------------------------------- =*/

    private void initPacketRegistryListeners()
    {
        //- Packet client registry
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_AUTHORIZATION_CHANNEL, new PacketServerAuthorizationResponseListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_EVENTBUS_CHANNEL, new PacketServerServiceInfoPublisherListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_EVENTBUS_CHANNEL, new PacketServerUpdatePermissionsListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_EVENTBUS_CHANNEL, new PacketServerChannelMessageNodeListener());
        //*= ------------------------------------
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerSetGlobalServiceInfoListListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerSetGroupConfigurationListListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerSetJsonFilePermissionsListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerSetDatabaseGroupFilePermissionsListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerSetServiceTaskListListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerDeployLocalTemplateListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CLUSTER_CHANNEL, new PacketServerClusterNodeInfoUpdateListener());
        //*= -------------------------------------
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_H2_DATABASE_UPDATE_MODULE, new PacketServerH2DatabaseListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_H2_DATABASE_UPDATE_MODULE, new PacketServerSetH2DatabaseDataListener());
        //*= -------------------------------------
        //Node server API
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CALLABLE_CHANNEL, new PacketClientCallablePacketReceiveListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CALLABLE_CHANNEL, new PacketClientSyncAPIPacketListener());
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_CALLABLE_CHANNEL, new PacketClusterSyncAPIPacketListener());
        //-
        this.getNetworkClient().getPacketRegistry().addListener(PacketConstants.INTERNAL_PACKET_CLUSTER_MESSAGE_CHANNEL, new PacketServerClusterChannelMessageListener());
        //-
        //- Packet server registry
        this.getNetworkServer().getPacketRegistry().addListener(PacketConstants.INTERNAL_AUTHORIZATION_CHANNEL, new PacketClientAuthorizationListener());
        //-
    }

    private void start0()
    {
        long value = System.currentTimeMillis();
        long millis = 1000 / TPS;
        int start1Tick = 0, start3Tick = 0 / 2;

        while (RUNNING)
        {
            try
            {
                long diff = System.currentTimeMillis() - value;
                if (diff < millis)
                    try
                    {
                        Thread.sleep(millis - diff);
                    } catch (Exception ignored)
                    {
                    }

                value = System.currentTimeMillis();

                while (!this.processQueue.isEmpty())
                    if (this.processQueue.peek() != null) Objects.requireNonNull(this.processQueue.poll()).call();
                    else this.processQueue.poll();

                if (start1Tick++ >= TPS)
                {
                    this.start1();
                    start1Tick = 0;
                }

                this.start2();

                if (start3Tick++ >= TPS)
                {
                    this.start3();
                    start3Tick = 0;
                }

                this.start4();

                eventManager.callEvent(new CloudNetTickEvent());

            } catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }

    private void start1()
    {
        for (ServiceTask serviceTask : cloudServiceManager.getServiceTasks())
            if (!serviceTask.isMaintenance())
                if ((serviceTask.getAssociatedNodes().isEmpty() || (serviceTask.getAssociatedNodes().contains(getConfig().getIdentity().getUniqueId()))) &&
                    serviceTask.getMinServiceCount() > cloudServiceManager.getServiceInfoSnapshots(serviceTask.getName()).size())
                {
                    if (competeWithCluster(serviceTask))
                    {
                        ICloudService cloudService = cloudServiceManager.runTask(serviceTask);

                        if (cloudService != null)
                            try
                            {
                                cloudService.start();
                            } catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                    }
                }
    }

    private void start2()
    {
        for (ICloudService cloudService : this.cloudServiceManager.getCloudServices().values())
            if (!cloudService.isAlive())
                cloudService.stop();
    }

    private void start3()
    {
        this.publishNetworkClusterNodeInfoSnapshotUpdate();
    }

    private void start4()
    {
        for (ICloudService cloudService : cloudServiceManager.getCloudServices().values())
            cloudService.getServiceConsoleLogCache().update();
    }

    private void unloadAll()
    {
        this.unloadModules();

        this.commandMap.unregisterCommands();
        this.eventManager.unregisterAll();
        this.servicesRegistry.unregisterAll();
        this.httpServer.clearHandlers();
    }

    private void unloadModules()
    {
        for (IModuleWrapper moduleWrapper : this.moduleProvider.getModules())
            if (!moduleWrapper.getModuleConfiguration().isRuntimeModule())
            {
                //unregister packet listeners
                this.unregisterPacketListenersByClassLoader(moduleWrapper.getClassLoader());

                moduleWrapper.unloadModule();
                this.logger.info(LanguageManager.getMessage("cloudnet-unload-module")
                    .replace("%module_group%", moduleWrapper.getModuleConfiguration().getGroup())
                    .replace("%module_name%", moduleWrapper.getModuleConfiguration().getName())
                    .replace("%module_version%", moduleWrapper.getModuleConfiguration().getVersion())
                    .replace("%module_author%", moduleWrapper.getModuleConfiguration().getAuthor()));
            }
    }

    private void unloadAllModules0()
    {
        for (IModuleWrapper moduleWrapper : this.moduleProvider.getModules())
        {
            //unregister packet listeners
            this.unregisterPacketListenersByClassLoader(moduleWrapper.getClassLoader());

            moduleWrapper.unloadModule();
            this.logger.info(LanguageManager.getMessage("cloudnet-unload-module")
                .replace("%module_group%", moduleWrapper.getModuleConfiguration().getGroup())
                .replace("%module_name%", moduleWrapper.getModuleConfiguration().getName())
                .replace("%module_version%", moduleWrapper.getModuleConfiguration().getVersion())
                .replace("%module_author%", moduleWrapper.getModuleConfiguration().getAuthor()));
        }
    }

    private void initDefaultConfigDefaultHostAddress() throws Exception
    {
        if (!this.config.isFileExists())
        {
            String input;

            do
            {
                if (System.getProperty("cloudnet.config.default-address") != null)
                {
                    this.config.setDefaultHostAddress(System.getProperty("cloudnet.config.default-address"));
                    break;
                }

                if (System.getenv("CLOUDNET_CONFIG_IP_ADDRESS") != null)
                {
                    this.config.setDefaultHostAddress(System.getenv("CLOUDNET_CONFIG_IP_ADDRESS"));
                    break;
                }

                logger.info(ConsoleColor.DARK_GRAY + LanguageManager.getMessage("cloudnet-init-config-hostaddress-input"));

                console.resetPrompt();
                console.setPrompt(ConsoleColor.WHITE.toString());
                input = console.readLineNoPrompt();
                console.setPrompt(ConsoleColor.DEFAULT.toString());
                console.resetPrompt();

                if (!input.equals("127.0.1.1") && input.split("\\.").length == 4)
                {
                    this.config.setDefaultHostAddress(input);
                    break;

                } else
                    logger.warning(ConsoleColor.RED + LanguageManager.getMessage("cloudnet-init-config-hostaddress-input-invalid"));

            } while (input != null);
        }
    }

    private boolean hasOneNodeToConnect()
    {
        return !getConfig().getClusterConfig().getNodes().isEmpty();
    }

    private void initDefaultPermissionGroups()
    {
        if (!hasOneNodeToConnect() && permissionManagement.getGroups().isEmpty() && System.getProperty("cloudnet.default.permissions.skip") == null)
        {
            IPermissionGroup adminPermissionGroup = new PermissionGroup("Admin", 100);
            adminPermissionGroup.addPermission("*");
            adminPermissionGroup.addPermission("Proxy", "*");
            adminPermissionGroup.setPrefix("&4Admin &8| &7");
            adminPermissionGroup.setSuffix("&f");
            adminPermissionGroup.setDisplay("&4");
            adminPermissionGroup.setSortId(10);

            permissionManagement.addGroup(adminPermissionGroup);

            IPermissionGroup defaultPermissionGroup = new PermissionGroup("default", 100);
            defaultPermissionGroup.addPermission("bukkit.broadcast.user", true);
            defaultPermissionGroup.setDefaultGroup(true);
            defaultPermissionGroup.setPrefix("&7");
            defaultPermissionGroup.setSuffix("&f");
            defaultPermissionGroup.setDisplay("&7");
            defaultPermissionGroup.setSortId(10);

            permissionManagement.addGroup(defaultPermissionGroup);
        }
    }

    private void initDefaultTasks() throws Exception
    {
        if (!hasOneNodeToConnect() && cloudServiceManager.getGroupConfigurations().isEmpty() && cloudServiceManager.getServiceTasks().isEmpty() &&
            System.getProperty("cloudnet.default.tasks.skip") == null)
        {
            boolean value = false;
            String input;

            do
            {
                if (value) break;

                if (System.getProperty("cloudnet.default.tasks.installation") != null)
                {
                    input = System.getProperty("cloudnet.default.tasks.installation");
                    value = true;

                } else if (System.getenv("CLOUDNET_DEFAULT_TASKS_INSTALLATION") != null)
                {
                    input = System.getenv("CLOUDNET_DEFAULT_TASKS_INSTALLATION");
                    value = true;

                } else
                {
                    logger.info(ConsoleColor.DARK_GRAY + LanguageManager.getMessage("cloudnet-init-default-tasks-input"));
                    logger.info(ConsoleColor.DARK_GRAY + LanguageManager.getMessage("cloudnet-init-default-tasks-input-list"));

                    console.resetPrompt();
                    console.setPrompt(ConsoleColor.WHITE.toString());
                    input = console.readLineNoPrompt();
                    console.setPrompt(ConsoleColor.DEFAULT.toString());
                    console.resetPrompt();
                }

                boolean doBreak = false;

                switch (input.trim().toLowerCase())
                {
                    case "recommended":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Test-Proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Test-Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task TestServer minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task CityBuild minecraft_server");

                        //Create groups
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create group Global-Server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create group Global-Proxy");

                        //Add groups
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy add group Global-Proxy");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Test-Proxy add group Global-Proxy");

                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby add group Global-Server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Test-Lobby add group Global-Server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task TestServer add group Global-Server");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task CityBuild add group Global-Server");

                        //Install
                        commandMap.dispatchCommand(consoleCommandSender, "lt create Global bukkit minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Global bukkit minecraft_server spigot-1.8.8");

                        commandMap.dispatchCommand(consoleCommandSender, "lt create Global proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Global proxy bungeecord default");

                        //Add templates
                        commandMap.dispatchCommand(consoleCommandSender, "tasks group Global-Server add template local Global bukkit");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks group Global-Proxy add template local Global proxy");

                        //Set configurations
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");

                        commandMap.dispatchCommand(consoleCommandSender, "tasks task TestServer set maxHeapMemory 256");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task TestServer set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task TestServer set autoDeleteOnStop false");

                        commandMap.dispatchCommand(consoleCommandSender, "tasks task CityBuild set maxHeapMemory 512");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task CityBuild set static true");
                        doBreak = true;
                        break;
                    case "java-bungee-1.7.10":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default bungeecord travertine");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default minecraft_server spigot-1.7.10");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "java-bungee-1.8.8":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default bungeecord default");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default minecraft_server spigot-1.8.8");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "java-bungee-1.13.2":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy bungeecord");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default bungeecord default");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default minecraft_server spigot-1.13.2");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "java-velocity-1.8.8":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy velocity");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default velocity default");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default minecraft_server spigot-1.8.8");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "java-velocity-1.13.2":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy velocity");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby minecraft_server");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default velocity default");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default minecraft_server spigot-1.13.2");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "bedrock":
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Proxy proxprox");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks create task Lobby nukkit");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Proxy default proxprox default");
                        commandMap.dispatchCommand(consoleCommandSender, "lt install Lobby default nukkit default");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Proxy set minServiceCount 1");
                        commandMap.dispatchCommand(consoleCommandSender, "tasks task Lobby set minServiceCount 1");
                        doBreak = true;
                        break;
                    case "nothing":
                        doBreak = true;
                        break;
                    default:
                        logger.warning(ConsoleColor.RED + LanguageManager.getMessage("cloudnet-init-default-tasks-input-invalid"));
                        break;
                }

                if (doBreak) break;

            } while (input != null);
        }
    }

    private void registerDefaultCommands()
    {
        this.logger.info(LanguageManager.getMessage("reload-register-defaultCommands"));

        this.commandMap.registerCommand(
            //Runtime commands
            new CommandHelp(),
            new CommandExit(),
            new CommandReload(),
            //Default commands
            new CommandClear(),
            new CommandTasks(),
            new CommandService(),
            new CommandCreate(),
            new CommandCluster(),
            new CommandModules(),
            new CommandLocalTemplate(),
            new CommandMe(),
            new CommandScreen(),
            new CommandPermissions()
        );
    }

    private <T> ITask<T> scheduleTask(Callable<T> callable)
    {
        ITask<T> task = new ListenableTask<>(callable);

        taskScheduler.schedule(task);
        return task;
    }

    private void enableModules()
    {
        loadModules();
        startModules();
    }

    private void loadModules()
    {
        this.logger.info(LanguageManager.getMessage("cloudnet-load-modules-createDirectory"));
        this.moduleDirectory.mkdirs();

        this.logger.info(LanguageManager.getMessage("cloudnet-load-modules"));
        for (File file : Objects.requireNonNull(this.moduleDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname)
            {
                String lowerName = pathname.getName().toLowerCase();
                return !pathname.isDirectory() && lowerName.endsWith(".jar") ||
                    lowerName.endsWith(".war") ||
                    lowerName.endsWith(".zip");
            }
        })))
        {
            this.logger.info(LanguageManager.getMessage("cloudnet-load-modules-found").replace("%file_name%", file.getName()));
            this.moduleProvider.loadModule(file);
        }
    }

    private void startModules()
    {
        for (IModuleWrapper moduleWrapper : this.moduleProvider.getModules())
            moduleWrapper.startModule();
    }

    private void enableCommandCompleter()
    {
        ((JLine2Console) console).getConsoleReader().addCompleter(new JLine2CommandCompleter(this.commandMap));
    }

    private void setDefaultRegistryEntries()
    {
        this.configurationRegistry.getString("permission_service", "json_database");
        this.configurationRegistry.getString("database_provider", "h2");

        this.configurationRegistry.save();
    }

    private void registerDefaultServices()
    {
        this.servicesRegistry.registerService(ITemplateStorage.class, LocalTemplateStorage.LOCAL_TEMPLATE_STORAGE,
            new LocalTemplateStorage(new File(System.getProperty("cloudnet.storage.local", "local/templates"))));

        this.servicesRegistry.registerService(IPermissionManagement.class, "json_file",
            new DefaultJsonFilePermissionManagement(new File(System.getProperty("cloudnet.permissions.json.path", "local/perms.json"))));

        this.servicesRegistry.registerService(IPermissionManagement.class, "json_database",
            new DefaultDatabasePermissionManagement(new Callable<AbstractDatabaseProvider>() {

                @Override
                public AbstractDatabaseProvider call() throws Exception
                {
                    return getDatabaseProvider();
                }
            }));

        this.servicesRegistry.registerService(AbstractDatabaseProvider.class, "h2",
            new H2DatabaseProvider(System.getProperty("cloudnet.database.h2.path", "local/database/h2"), taskScheduler));
    }

    private void runConsole()
    {
        Thread console = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try
                {
                    if (!getCommandLineArguments().contains("--noconsole"))
                    {
                        logger.info(LanguageManager.getMessage("console-ready"));

                        String input;
                        while ((input = getConsole().readLine()) != null)
                            try
                            {
                                if (input.trim().isEmpty()) continue;

                                CommandPreProcessEvent commandPreProcessEvent = new CommandPreProcessEvent(input, getConsoleCommandSender());
                                getEventManager().callEvent(commandPreProcessEvent);

                                if (commandPreProcessEvent.isCancelled()) continue;

                                if (!getCommandMap().dispatchCommand(getConsoleCommandSender(), input))
                                {
                                    getEventManager().callEvent(new CommandNotFoundEvent(input));
                                    logger.warning(LanguageManager.getMessage("command-not-found"));

                                    continue;
                                }

                                getEventManager().callEvent(new CommandPostProcessEvent(input, getConsoleCommandSender()));

                            } catch (Throwable ex)
                            {
                                ex.printStackTrace();
                            }
                    } else while (RUNNING) Thread.sleep(1000);
                } catch (Throwable ex)
                {
                    ex.printStackTrace();
                }
            }
        });

        console.setName("Console-Thread");
        console.setPriority(Thread.MIN_PRIORITY);
        console.setDaemon(true);
        console.start();
    }
}