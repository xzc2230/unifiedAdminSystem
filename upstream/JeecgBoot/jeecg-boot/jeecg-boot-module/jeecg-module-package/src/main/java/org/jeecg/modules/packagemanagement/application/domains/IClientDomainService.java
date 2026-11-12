package org.jeecg.modules.packagemanagement.application.domains;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.domain.ClientDomain;

public interface IClientDomainService {
    ClientDomain selectClientDomainById(Long id);
    List<ClientDomain> selectClientDomainList(ClientDomain filter);
    int insertClientDomain(ClientDomain row);
    int updateClientDomain(ClientDomain row);
    int deleteClientDomainByIds(Long[] ids);
    void syncPending();
}
