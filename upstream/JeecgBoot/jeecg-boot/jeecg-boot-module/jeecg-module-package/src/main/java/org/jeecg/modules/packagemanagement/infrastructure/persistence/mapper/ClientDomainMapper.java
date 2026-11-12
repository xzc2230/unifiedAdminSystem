package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.domain.ClientDomain;

public interface ClientDomainMapper {
    ClientDomain selectClientDomainById(Long id);
    ClientDomain selectClientDomainByApex(String apex);
    List<ClientDomain> selectClientDomainList(ClientDomain filter);
    List<ClientDomain> selectClientDomainByStatus(String status);
    List<ClientDomain> selectClientDomainByStatuses(List<String> statuses);
    int insertClientDomain(ClientDomain row);
    int updateClientDomain(ClientDomain row);
    int deleteClientDomainByIds(Long[] ids);
}
