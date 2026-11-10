package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolve notify recipients: row email first, then global config (option B).
 */
public final class PackStatNotifyEmailSupport
{
    private PackStatNotifyEmailSupport()
    {
    }

    public static String resolve(String rowEmail, String globalEmail)
    {
        String row = trimToNull(rowEmail);
        if (row != null)
        {
            return row;
        }
        return trimToNull(globalEmail);
    }

    public static String[] splitRecipients(String emails)
    {
        String value = trimToNull(emails);
        if (value == null)
        {
            return new String[0];
        }
        String[] parts = value.split(",");
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++)
        {
            String one = trimToNull(parts[i]);
            if (one != null)
            {
                list.add(one);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
