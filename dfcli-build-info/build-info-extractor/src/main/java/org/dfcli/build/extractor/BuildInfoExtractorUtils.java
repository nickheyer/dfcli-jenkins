package org.dfcli.build.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BuildInfoExtractorUtils {
    public static ObjectMapper createMapper() {
        return new ObjectMapper();
    }
}
