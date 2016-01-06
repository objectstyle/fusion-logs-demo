package com.objectstyle.fusion.connectors;


import com.lucidworks.apollo.pipeline.index.config.transform.FieldMappingConfig;
import com.lucidworks.apollo.pipeline.schema.IntegerType;
import com.lucidworks.apollo.pipeline.schema.StringType;
import com.lucidworks.apollo.pipeline.schema.UIHints;
import com.lucidworks.apollo.pipeline.schema.validation.ValidationError;
import com.lucidworks.common.models.DataSource;
import com.lucidworks.common.models.DataSourceConstants;
import com.lucidworks.common.models.DataSourceType;
import com.lucidworks.connectors.ConnectorUtils;
import com.lucidworks.utils.FieldMappingUtil;
import com.lucidworks.utils.StringUtils;

import java.util.List;

public class LogFileType extends DataSourceType {

    public static final String THREAD_COUNT = "threadCount";
    public static final int DEFAULT_THREAD_COUNT = 4;

    protected LogFileType() {
        super(DataSourceConstants.CATEGORY_FS, "File system", "Log File");
    }

    @Override
    public List<ValidationError> validate(DataSource ds) {
        List<ValidationError> errors = super.validate(ds);
        // check reachability, default to true
        if (ds.getProperty(DataSourceConstants.VERIFY_ACCESS) != null &&
                !StringUtils.getBoolean(ds.getProperty(DataSourceConstants.VERIFY_ACCESS))) {
            return errors;
        }
        // check only if we don't have other errors
        if (errors.isEmpty()) {
            String path = (String)ds.getProperty(DataSourceConstants.PATH);
            ConnectorUtils.fileReachabilityCheck(path, errors);
        }
        return errors;

    }

    // default behavior is to return null - i.e. field mapping not supported
    @Override
    public FieldMappingConfig getInitialFieldMapping() {
        return FieldMappingUtil.defaultFieldMapping();
    }

    @Override
    protected void addConnectorSpecificSchema() {
        // we require a non-blank string - we could have also implemented
        // our own validation or provided a default path...
        schema
                .withProperty(DataSourceConstants.PATH,
                        StringType.create().withTitle("Root path")
                                .withMinLength(1)
                                .withHints(UIHints.LENGTHY));

        schema.withProperty(THREAD_COUNT,
                IntegerType.create().withTitle("Thread count")
                        .withDefaultValue((long) DEFAULT_THREAD_COUNT));
        // this source supports boundary limits (exclude/include/bounds) options
        addBoundaryLimitsSchema();
        // this source supports reachability check during DS creation
        addVerifyAccessSchema();
        // this source support commit-related options
        addCommitSchema();
    }

}
