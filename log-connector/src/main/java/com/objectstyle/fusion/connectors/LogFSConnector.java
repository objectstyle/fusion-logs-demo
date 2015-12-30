package com.objectstyle.fusion.connectors;


import com.lucidworks.connectors.Connector;

public class LogFSConnector extends Connector {

    public static final String LOG_FILE_TYPE = "logFile";

    @Override
    public void init() {
        super.init();
        registerDataSourceType(LOG_FILE_TYPE, new LogFileType());
    }
}
