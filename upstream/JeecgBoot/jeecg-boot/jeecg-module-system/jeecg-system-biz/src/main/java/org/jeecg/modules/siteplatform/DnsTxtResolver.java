package org.jeecg.modules.siteplatform;

import org.springframework.stereotype.Component;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

@Component
public class DnsTxtResolver {
    public Set<String> records(String name) {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "1500");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        InitialDirContext context = null;
        try {
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(name, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            Set<String> result = new HashSet<>();
            if (txt != null) {
                NamingEnumeration<?> values = txt.getAll();
                try { while (values.hasMore()) result.add(values.next().toString().replace("\"", "").replace(" ", "")); }
                finally { values.close(); }
            }
            return result;
        } catch (NamingException e) { throw new ResourceFailure(422, "未取得 DNS TXT 记录，请检查记录并等待解析生效后重试"); }
        finally { if (context != null) try { context.close(); } catch (NamingException ignored) { /* no credentials in resolver */ } }
    }
}
