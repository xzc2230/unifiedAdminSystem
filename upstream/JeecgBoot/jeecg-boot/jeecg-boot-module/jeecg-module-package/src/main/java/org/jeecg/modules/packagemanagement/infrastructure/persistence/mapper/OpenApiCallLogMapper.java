package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;
import java.util.List;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiCallLog;
public interface OpenApiCallLogMapper {
 List<OpenApiCallLog> list(OpenApiCallLog query);
 int insert(OpenApiCallLog row);
}
