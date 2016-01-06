package com.objectstyle.fusion.connectors;

import com.lucidworks.apollo.common.pipeline.PipelineDocument;
import com.lucidworks.common.models.Checkpoint;
import com.lucidworks.common.models.DataSourceConstants;
import com.lucidworks.common.models.Defaults;
import com.lucidworks.common.models.JobStatus;
import com.lucidworks.connectors.ConnectorException;
import com.lucidworks.connectors.ConnectorJob;
import com.lucidworks.connectors.Fetcher;
import com.lucidworks.connectors.datasource.DataSourceUtils;
import com.lucidworks.connectors.pipeline.PipelineConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class LogFetcher extends Fetcher {

    private static final Logger LOG = LoggerFactory.getLogger(LogFetcher.class);

    private int threadCount;
    private long maxSize;
    private int depth;
    private boolean stopped = false;

    private ExecutorService executorService;

    public void init(ConnectorJob job) throws ConnectorException {
        super.init(job);
        threadCount = job.getDataSource().getInt(LogFileType.THREAD_COUNT, LogFileType.DEFAULT_THREAD_COUNT);
        maxSize = job.getDataSource().getLong(DataSourceConstants.MAX_BYTES,
                Defaults.INSTANCE.getInt(Defaults.Group.datasource, DataSourceConstants.MAX_BYTES));
        depth = job.getDataSource().getInt(DataSourceConstants.CRAWL_DEPTH,
                Defaults.INSTANCE.getInt(Defaults.Group.datasource, DataSourceConstants.CRAWL_DEPTH));
        if (depth < 1) {
            depth = Integer.MAX_VALUE;
        }
    }

    @Override
    public void start(Checkpoint lastCheckpoint) throws ConnectorException {
        // mark as starting
        job.getJobStatus().starting();
        try {
            this.executorService = Executors.newFixedThreadPool(threadCount);
            //Look up what type of crawler we are and then kick off the appropriate version
            runFileCrawl();
        } catch (Throwable t) {
            LOG.warn("Exception during file crawl", t);
            job.getJobStatus().failed(t);//keeps track of failed crawls.
        } finally {
            //Update our status
            if (stopped) {
                job.getJobStatus().end(JobStatus.State.STOPPED);
            } else {
                job.getJobStatus().end(JobStatus.State.FINISHED);
            }
            executorService.shutdown();
        }
    }

    @Override
    public void stop() throws ConnectorException {
        super.stop();
        this.stopped = true;
    }

    private void runFileCrawl() throws Exception {
        String path = DataSourceUtils.getPath(job.getDataSource());
        File root = new File(path);
        // mark as running
        job.getJobStatus().running();
        int curDepth = 0;

        List<File> files = new ArrayList<>();

        traverse(root, curDepth, files);

        List<Future<?>> fileReadFutures = new ArrayList<>();

        for (final File file : files) {
            fileReadFutures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    readFile(file);
                }
            }));
        }

        Iterator<Future<?>> futureIterator = fileReadFutures.iterator();

        while (futureIterator.hasNext() && !stopped) {
            futureIterator.next().get();
        }
    }

    /*
     * Traverse the file system hierarchy up to a depth.
     */
    private void traverse(final File f, int curDepth, List<File> fileAccumulator) {
        if (curDepth > depth || stopped) {
            return;
        }
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            for (File file : files) {
                traverse(file, curDepth + 1, fileAccumulator);
            }
        } else {
            if (!f.canRead()) {//We can't read this file, so mark it as failed.
                job.getJobStatus().incrementCounter(PipelineConstants.FAILED_COUNT);
                return;
            }

            fileAccumulator.add(f);
        }
    }

    private void readFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);

            Map<String, List<LogLine>> requestMap = new HashMap<>();

            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            String line;
            LogLine logLine = null;

            while ((line = reader.readLine()) != null && !stopped) {
                if (LogLine.isCompleteLogLine(line)) {
                    logLine = LogLine.fromString(line);
                } else if (logLine != null) {
                    // append multiline stack trace part to the last found log line
                    logLine.setLogMessage(logLine.getLogMessage() + "\n" + line);
                } else {
                    // log file starts with detached stack trace line... skipping...
                    continue;
                }

                if (requestMap.get(logLine.getThreadId()) == null) {
                    requestMap.put(logLine.getThreadId(), new ArrayList<LogLine>());
                }

                requestMap.get(logLine.getThreadId()).add(logLine);

                if (logLine.isRequestEndMarker()) {
                    List<LogLine> requestLines = requestMap.get(logLine.getThreadId());

                    job.getPipeline().process(createDocument(requestLines, file.getName()), job.getPipelineContext());
                    job.getJobStatus().incrementCounter(PipelineConstants.NEW_COUNT);
                    job.getJobStatus().incrementCounter(PipelineConstants.OUTPUT_COUNT);

                    requestMap.remove(logLine.getThreadId());
                }
            }

            job.getJobStatus().incrementCounter(PipelineConstants.INPUT_COUNT);
        } catch (Exception e) {
            LOG.error("Couldn't read file: {}", file.getAbsolutePath(), e);
            job.getJobStatus().incrementCounter(PipelineConstants.FAILED_COUNT);
        }
    }

    private PipelineDocument createDocument(List<LogLine> logLines, String fileName) {
        PipelineDocument doc = new PipelineDocument();

        LogLine startLine = logLines.get(0);

        doc.setField("timestamp_dt", startLine.getTimestamp());
        doc.setField("thread_id_s", startLine.getThreadId());

        StringBuilder logMessageBuilder = new StringBuilder();

        for (LogLine line : logLines) {
            if (logMessageBuilder.length() > 0) {
                logMessageBuilder.append("\n");
            }
            logMessageBuilder.append(line.getLogMessage());
        }

        LOG.info("New log document: {}", logMessageBuilder.toString());

        doc.setField("log_message_txt", logMessageBuilder.toString());
        doc.setContent(logMessageBuilder.toString().getBytes());
        doc.setId(fileName + "/" + startLine.getThreadId() + "/" + startLine.getTimestamp().getTime());

        return doc;
    }
}
