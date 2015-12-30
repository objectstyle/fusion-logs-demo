package com.objectstyle.fusion.connectors;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogLine {
	
    private static final Pattern LOG_LINE_PATTERN = Pattern.compile("\\[(.*)\\] ([a-zA-Z0-9 -]*) \\{\\} ([A-Z]*) (.*)");
    private static final Pattern REQUEST_START_END_PATTERN = Pattern.compile("LogFilter: ([a-z]*) (.*)");

    private static final DateFormat TIMESTAMP_DATE_FORMAT = new SimpleDateFormat("dd/MMM/yyyy:H:mm:ss,SSS");

    private static final String START_MARKER = "start";
    private static final String END_MARKER = "end";

    private Date timestamp;
    private String level;
    private String threadId;
    private String logMessage;
    private boolean requestStartMarker = false;
    private boolean requestEndMarker = false;

    public static boolean isCompleteLogLine(String line) {
        Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(line);
        return logLineMatcher.find();
    }

    public static LogLine fromString(String line) throws ParseException {
        Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(line);

        if (logLineMatcher.find()) {
            String timestampStr = logLineMatcher.group(1);
            String threadId = logLineMatcher.group(2);
            String level = logLineMatcher.group(3);
            String logMessage = logLineMatcher.group(4);

            Date timestamp = TIMESTAMP_DATE_FORMAT.parse(timestampStr);

            LogLine logLine = new LogLine();
            logLine.setTimestamp(timestamp);
            logLine.setLevel(level);
            logLine.setThreadId(threadId);
            logLine.setLogMessage(logMessage);

            Matcher requestStartEndMatcher = REQUEST_START_END_PATTERN.matcher(logMessage);

            if (requestStartEndMatcher.find()) {
                String marker = requestStartEndMatcher.group(1);

                if (START_MARKER.equals(marker)) {
                    logLine.setRequestStartMarker(true);
                } else if (END_MARKER.equals(marker)) {
                    logLine.setRequestEndMarker(true);
                } else {
                    throw new IllegalStateException("Could not parse request start/end marker.");
                }
            }

            return logLine;
        }

        throw new IllegalArgumentException("Unparseable log line: " + line);
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public boolean isRequestStartMarker() {
        return requestStartMarker;
    }

    public void setRequestStartMarker(boolean requestStartMarker) {
        this.requestStartMarker = requestStartMarker;
    }

    public boolean isRequestEndMarker() {
        return requestEndMarker;
    }

    public void setRequestEndMarker(boolean requestEndMarker) {
        this.requestEndMarker = requestEndMarker;
    }
}
