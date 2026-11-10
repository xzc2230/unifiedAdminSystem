package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
public interface OpenApiTokenMapper {
 OpenApiToken byId(@Param("id")long id);
 OpenApiToken byToken(@Param("token")String token);
 List<OpenApiToken> list(OpenApiToken query);
 int insert(OpenApiToken row);
 int update(OpenApiToken row);
 int delete(@Param("id")long id,@Param("tenantId")int tenantId);
}
