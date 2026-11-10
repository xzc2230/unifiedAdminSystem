package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
public interface OpenApiManagerMapper {
 Long lockStoreTenant(@Param("id")long id,@Param("tenant")int tenant);
 AppManager lockById(@Param("id")long id);
}
