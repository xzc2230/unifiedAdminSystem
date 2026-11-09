package org.jeecg.modules.packagemanagement.application.legacy;

import java.util.Map;

public class ParamsUtils {

    public static String getParams(Map<String, String[]> params) {

        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String[] values = entry.getValue();
            for (String value : values) {
                if (value.contains(".")) {
                    return value;
                }
            }
        }
        return "";
    }
}
