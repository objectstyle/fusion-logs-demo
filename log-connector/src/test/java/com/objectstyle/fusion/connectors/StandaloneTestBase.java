package com.objectstyle.fusion.connectors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyStore;

import com.lucidworks.utils.TempFileUtils;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.TestingZooKeeperServer;
import org.codehaus.jackson.map.ObjectMapper;
import com.google.inject.TypeLiteral;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Assert;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.lucidworks.apollo.common.jackson.ObjectMapperFactory;
import com.lucidworks.apollo.component.ConfigurationComponent;
import com.lucidworks.apollo.component.CuratorComponent;
import com.lucidworks.apollo.pipeline.StageConfig;
import com.lucidworks.apollo.pipeline.index.IndexStage;
import com.lucidworks.apollo.pipeline.index.stages.transform.FieldMappingStage;
import com.lucidworks.apollo.security.DefaultStringDecryptorProvider;
import com.lucidworks.apollo.security.DefaultStringEncryptorProvider;
import com.lucidworks.apollo.security.KeyRingProvider;
import com.lucidworks.apollo.security.KeyStoreProvider;
import com.lucidworks.apollo.security.crypto.KeyRing;
import com.lucidworks.apollo.security.crypto.StringDecryptor;
import com.lucidworks.apollo.security.crypto.StringEncryptor;
import com.lucidworks.apollo.service.ApolloGuiceContextListener;
import com.lucidworks.Constants;
import com.lucidworks.common.models.DataSource;
import com.lucidworks.common.models.Defaults;
import com.lucidworks.common.models.JobStatus;
import com.lucidworks.connectors.Connector;
import com.lucidworks.connectors.ConnectorManager;
import com.lucidworks.connectors.ConnectorRegistry;
import com.lucidworks.connectors.ConnectorUtils;
import com.lucidworks.connectors.DataSourceManager;
import com.lucidworks.connectors.ZKDataSourceManager;
import com.lucidworks.connectors.pipeline.ApolloPipelineWrapper;
import com.lucidworks.connectors.pipeline.PipelineProcessor;
import com.lucidworks.utils.RandomId;
import com.netflix.config.ConfigurationManager;
import com.netflix.governator.configuration.ArchaiusConfigurationProvider;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;

/**
 * Test base class heavily based on StandaloneTestBase from Fusion's example connector with some
 * minor configuration changes.
 */
public class StandaloneTestBase extends Assert {

    public static org.slf4j.Logger LOG = LoggerFactory.getLogger(StandaloneTestBase.class);

    protected static TestingCluster testZooKeeper;
    protected static ConnectorManager connectorManager;
    protected static ConnectorRegistry ccr;
    protected static int port;
    private static File apolloConf;
    protected static AbstractConfiguration configuration;
    protected static Injector injector;
    public final static File CONNECTORS_BASEDIR;
    public static File CONNECTORS_TEST_ROOT_DIR;
    public final static File CONNECTORS_TEST_DATA_DIR;
    public final static File CONNECTORS_TEST_CONF_DIR;
    public static File resourcesDir;

    static {
        CONNECTORS_BASEDIR = new File(System.getProperty("connectors.basedir", "."));

        CONNECTORS_TEST_ROOT_DIR = new File(System.getProperty("connectors.test.root.dir") + File.separator + "connectors-test-root-dir-" + System.nanoTime());

        CONNECTORS_TEST_DATA_DIR = new File(CONNECTORS_TEST_ROOT_DIR, "data");
        System.setProperty("apollo.connectors.data", CONNECTORS_TEST_DATA_DIR.getAbsolutePath());

        CONNECTORS_TEST_CONF_DIR = new File(CONNECTORS_TEST_ROOT_DIR, "conf");
        System.setProperty("apollo.connectors.conf", CONNECTORS_TEST_CONF_DIR.getAbsolutePath());

        resourcesDir = new File("src/test/resources");
    }

    @BeforeClass
    public static void beforeTestBase() throws Exception {
        // Need to intercept the init chain here to override ZK connect string
        testZooKeeper = new TestingCluster(1);

        testZooKeeper.start();

        ConfigurationManager.loadPropertiesFromResources(getPropertiesFilename());
        // set this here so that ConnectorsClientTest can use it
        port = getRandomPort();
        String curatorNamespace = getCuratorNamespace();
        // create local overrides for configuration
        apolloConf = genTempPath("apollo-conf" + System.nanoTime());
        apolloConf.mkdirs();
        FileWriter fw = new FileWriter(new File(apolloConf, "config-local.properties"));
        fw.write(Constants.CURATOR_ZK_CONNECT + "=" + testZooKeeper.getConnectString() + "\n");
        fw.write(Constants.CURATOR_NAMESPACE + "=" + curatorNamespace + "\n");
        fw.write(ConfigurationComponent.SOLR_ZK_CONNECT + "=" + testZooKeeper.getConnectString() + "\n");
        fw.write("com.lucidworks.app.port=" + port + "\n");
        fw.flush();
        fw.close();
        System.setProperty("conf.dir", apolloConf.getAbsolutePath());
        configuration = ConfigurationManager.getConfigInstance();
        configuration.setProperty(Constants.CURATOR_NAMESPACE, curatorNamespace);
        configuration.setProperty(Constants.CURATOR_ZK_CONNECT, testZooKeeper.getConnectString());
        configuration.setProperty(ConfigurationComponent.SOLR_ZK_CONNECT, testZooKeeper.getConnectString());
        final ArchaiusConfigurationProvider provider = ArchaiusConfigurationProvider.builder()
                .withConfigurationManager(configuration).build();
        LifecycleInjector lifecycleInjector = LifecycleInjector.builder().withBootstrapModule(
                new BootstrapModule() {
                    @Override
                    public void configure(BootstrapBinder binder) {
                        binder.bindConfigurationProvider().toInstance(provider);
                        binder.bind(Defaults.class).asEagerSingleton();
                        binder.bind(DataSourceManager.class).to(ZKDataSourceManager.class);
                        binder.bind(PipelineProcessor.class).to(ApolloPipelineWrapper.class);
                        binder.bind(ObjectMapper.class).toInstance(ObjectMapperFactory.create());
                        binder.bind(StringDecryptor.class).toProvider(DefaultStringDecryptorProvider.class);
                        binder.bind(StringEncryptor.class).toProvider(DefaultStringEncryptorProvider.class);
                        binder.bind(KeyRing.class).toProvider(KeyRingProvider.class);
                        binder.bind(KeyStore.class).toProvider(KeyStoreProvider.class);
                        ApolloGuiceContextListener.applyWhenReadyBinder(binder);
                        Multibinder<IndexStage<? extends StageConfig>> mbinder = Multibinder.newSetBinder(binder,
                                new TypeLiteral<IndexStage<? extends StageConfig>>() {});
                        mbinder.addBinding().to(FieldMappingStage.class);
                    }
                }
        )
                .build();
        LOG.info("Starting lifecycleInjector");
        lifecycleInjector.getLifecycleManager().start();
        LOG.info("Creating injector");
        injector = lifecycleInjector.createInjector();

        Defaults.INSTANCE = injector.getInstance(Defaults.class);
        Defaults.injector = injector;
        Thread.sleep(5000);

        ccr = injector.getInstance(ConnectorRegistry.class);
        connectorManager = injector.getInstance(ConnectorManager.class);
    }

    @AfterClass
    public static void afterConnectorTestBase() throws Exception {
        // shutdown other guice components
        if (injector != null) {
            ConnectorManager cm = injector.getInstance(ConnectorManager.class);
            cm.shutdown();
            ConnectorRegistry ccr = injector.getInstance(ConnectorRegistry.class);
            ccr.shutdown();
            CuratorComponent curator = injector.getInstance(CuratorComponent.class);
            curator.shutdown();
            Defaults.injector  = null;
        }
        if (apolloConf != null) {
            FileUtils.deleteDirectory(apolloConf);
        }
        configuration = null;
        injector = null;
        testZooKeeper.close();
        // kill ZK servers
        for (TestingZooKeeperServer s : testZooKeeper.getServers()) {
            s.kill();
        }
        testZooKeeper = null;
    }

    protected JobStatus syncCrawlNewDs(DataSource ds, PipelineProcessor cp) throws Exception {
        Connector cc = ccr.get(ds.getConnector());
        if (cc == null) {
            throw new Exception("crawler type " + ds.getConnector() + " not found");
        }
        connectorManager.addDataSource(ds);
        String cid = cc.startJob(ds.getId(), cp);
        ConnectorUtils.waitJob(cc, cid);

        return cc.getJobStatus(cid);
    }

    protected static int getRandomPort() {
        ServerSocket server = null;
        try {
            server = new ServerSocket(0);
            return server.getLocalPort();
        } catch (IOException e) {
            throw new Error(e);
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * Override this method to change the name of the properties file to load, default is test.properties
     */
    public static String getPropertiesFilename() {
        return "test.properties";
    }

    /**
     * Override this method to change Curator namespace, default is random (UUID4)
     */
    public static String getCuratorNamespace() {
        return "lucid-" + RandomId.create();
    }


    /**
     * Generates a path to (but does not create on disk) a temporary
     * directory with an understandable (but unique) name.
     * This directory will not be cleaned up automaticly.
     * params will not be checked for special characters
     */
    public static File genTempPath(String prefix, String... items) {
        return TempFileUtils.genTempPath(CONNECTORS_TEST_ROOT_DIR, prefix, items);
    }
}
