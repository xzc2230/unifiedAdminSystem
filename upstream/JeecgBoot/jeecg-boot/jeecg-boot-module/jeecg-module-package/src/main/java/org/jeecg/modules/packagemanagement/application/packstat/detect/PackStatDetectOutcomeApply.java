package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.sql.Date;
import java.time.LocalDate;
import org.jeecg.modules.packagemanagement.application.packstat.util.PackStatDateCalc;

/**
 * After each valid probe, refresh duration fields; delisted also sets drop date.
 */
public final class PackStatDetectOutcomeApply
{
    private PackStatDetectOutcomeApply()
    {
    }

    public static StatsUpdate apply(PackStatDetectResult result, LocalDate passDate,
            LocalDate customerUseDate, LocalDate today)
    {
        if (result == null || result == PackStatDetectResult.INVALID)
        {
            return null;
        }
        LocalDate end = today == null ? LocalDate.now() : today;
        StatsUpdate update = new StatsUpdate();
        update.setSurviveDays(PackStatDateCalc.surviveDays(passDate, end));
        update.setUseDays(PackStatDateCalc.useDays(customerUseDate, end));
        if (result == PackStatDetectResult.DELISTED)
        {
            update.setDropDate(Date.valueOf(end));
        }
        return update;
    }

    public static final class StatsUpdate
    {
        private java.util.Date dropDate;
        private Integer surviveDays;
        private Integer useDays;

        public java.util.Date getDropDate()
        {
            return dropDate;
        }

        public void setDropDate(java.util.Date dropDate)
        {
            this.dropDate = dropDate;
        }

        public Integer getSurviveDays()
        {
            return surviveDays;
        }

        public void setSurviveDays(Integer surviveDays)
        {
            this.surviveDays = surviveDays;
        }

        public Integer getUseDays()
        {
            return useDays;
        }

        public void setUseDays(Integer useDays)
        {
            this.useDays = useDays;
        }
    }
}
