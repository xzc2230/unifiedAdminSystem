package org.jeecg.modules.packagemanagement.application.port;
public interface AppLogPort {
record Page(java.util.List<org.jeecg.modules.packagemanagement.domain.applog.AppLog> rows,long total){}
 org.jeecg.modules.packagemanagement.domain.applog.AppLog save(org.jeecg.modules.packagemanagement.domain.applog.AppLog log);
 java.util.Optional<org.jeecg.modules.packagemanagement.domain.applog.AppLog> findById(String id);
 Page query(org.jeecg.modules.packagemanagement.domain.applog.AppLog filter,int page,int size,java.util.Collection<String> allowedPackages);
 void deleteAllById(java.lang.Iterable<String> ids);
 long clean(org.jeecg.modules.packagemanagement.domain.applog.AppLog filter,java.util.Collection<String> allowedPackages);
}
