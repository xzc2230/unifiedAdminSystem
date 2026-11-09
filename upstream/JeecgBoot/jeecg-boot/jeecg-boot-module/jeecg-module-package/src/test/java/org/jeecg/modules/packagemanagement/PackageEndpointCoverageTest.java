package org.jeecg.modules.packagemanagement;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.controller.legacy.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageEndpointCoverageTest {
    @TempDir Path files;
    @TestFactory Collection<DynamicTest> all99SourceActionsResolveToExpectedHandler() throws Exception {
        var registry=mock(PackageAdminRegistry.class);var guard=mock(PackageAdminSupport.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PackageAdminController(registry,guard),
            new PackageAdminActionsController(registry,guard,null,null,null),new PackageRequestLogController(null,guard,null),
            new LegacyClientController(null),new LegacyPublicController(null,null,null,null,null,null,null,null),
            new PackageFileController(files.toString())).build();
        var mapping=mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
        Path directory=Path.of("").toAbsolutePath();
        while(directory!=null&&!Files.exists(directory.resolve("docs/migration/package-api/target-endpoints.json")))directory=directory.getParent();
        assertNotNull(directory,"Repository manifest must be available");
        var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(directory.resolve("docs/migration/package-api/target-endpoints.json")).replace("\uFEFF",""));
        assertEquals(99,manifest.path("endpoints").size());
        var tests=new ArrayList<DynamicTest>();
        for(var row:manifest.path("endpoints")) {
            String path=row.path("targetPath").asText().replaceAll("\\{[^}]+}","1");
            String method=row.path("targetMethod").asText();String expected=row.path("targetHandler").asText();
            tests.add(DynamicTest.dynamicTest(row.path("sourceMethod").asText()+" "+row.path("sourcePath").asText(),()->{
                for(String verb:method.equals("ANY")?List.of("GET","POST","PUT","DELETE","PATCH","HEAD"):List.of(method)) {
                    var request=new MockHttpServletRequest(verb,path);request.setServletPath(path);
                    org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
                    var chain=mapping.getHandler(request);assertNotNull(chain,verb+" "+path);
                    HandlerMethod handler=(HandlerMethod)chain.getHandler();
                    assertEquals(expected,handler.getBeanType().getSimpleName()+"#"+handler.getMethod().getName(),verb+" "+path);
                }
            }));
        }
        return tests;
    }
}
