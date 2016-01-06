package com.objectstyle.fusion.connectors;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.lucidworks.apollo.common.pipeline.PipelineDocument;
import com.lucidworks.apollo.pipeline.PipelineContext;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lucidworks.common.models.DataSource;
import com.lucidworks.common.models.DataSourceConstants;
import com.lucidworks.common.models.JobStatus;
import com.lucidworks.common.models.JobStatus.State;
import com.lucidworks.connectors.Connector;
import com.lucidworks.connectors.mock.MockPipeline;
import com.lucidworks.connectors.mock.MockSink;
import com.lucidworks.connectors.pipeline.PipelineConstants;
import com.lucidworks.connectors.pipeline.PipelineProcessor;


public class LogConnectorTest extends StandaloneTestBase {

    private static Connector cc;
    private MockSink mock = new MockSink();
    public static final String COLLECTION = "collection1";

    @BeforeClass
    public static void init() {

        System.out.println("ConnectorsCount: " + ccr.getConnectors().size());
        for (String name : ccr.getConnectors().keySet()) {
            System.out.println(name);
        }
        cc = ccr.get("objectstyle.logs");
    }

    @Test
    public void testLogConnector() throws Exception {
        // take connector source files
        DataSource ds = new DataSource("file", "objectstyle.logs");
        ds.setDescription("logFile");
        ds.setProperty(DataSourceConstants.PATH, new File("src/test/resources/sample-logs").getAbsolutePath());
        // make results visible when crawl is finished
        ds.setProperty(DataSourceConstants.COMMIT_ON_FINISH, true);
        ds.setCollection(COLLECTION);
        ds.setPipeline("example");
        ds = cc.validate(ds);

        final Map<String, PipelineDocument> docs = new HashMap<>();

        // start crawl
        PipelineProcessor cp = new MockPipeline(mock) {
            @Override
            public void process(PipelineDocument doc, PipelineContext ctx) throws Exception {
                // collect all docs put into pipeline skipping Fusion internal documents with null id
                if (doc.getId() != null) {
                    docs.put(doc.getId(), doc);
                }
                super.process(doc, ctx);
            }
        };

        JobStatus status = syncCrawlNewDs(ds, cp);

        assertNotNull("Should get non-null status", status);
        assertEquals("Should be FINISHED", State.FINISHED,
                status.getState());

        long newDocCount = status.getCounter(PipelineConstants.NEW_COUNT);
        assertEquals("Should create 8 documents", 8, newDocCount);

        assertEquals(8, docs.size());

        // TODO: compare generated docs with the ones we expect to get
    }

}
