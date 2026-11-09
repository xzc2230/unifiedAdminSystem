package org.jeecg.modules.packagemanagement;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.jeecg.modules.packagemanagement.controller.legacy.PackageFileController;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PackageFileRoutesTest {
    @TempDir Path root;
    @Test void uploadDownloadAndRejectTraversal() throws Exception {
        var controller=new PackageFileController(root.toString());
        var mvc=MockMvcBuilders.standaloneSetup(controller,new org.jeecg.modules.packagemanagement.controller.legacy.LegacyClientController(null)).build();
        var response=mvc.perform(multipart("/package-legacy/common/upload").file(new MockMultipartFile("file","hello.txt","text/plain","hello".getBytes()))).andReturn().getResponse();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertEquals(200,json.path("code").asInt());String resource=json.path("fileName").asText();
        assertTrue(resource.startsWith("/profile/upload/"));
        assertEquals("hello",mvc.perform(get("/package-legacy/common/download/resource").param("resource",resource)).andReturn().getResponse().getContentAsString());
        assertEquals("hello",mvc.perform(get("/package-legacy"+resource)).andReturn().getResponse().getContentAsString());
        assertEquals(400,mvc.perform(get("/package-legacy/common/download/resource").param("resource","/profile/../secret.txt")).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/package-legacy/common/download").param("fileName","../../secret.txt")).andReturn().getResponse().getStatus());
        Files.createDirectories(root.resolve("upload"));Files.writeString(root.resolve("upload/keep.txt"),"keep");
        assertEquals(400,mvc.perform(get("/package-legacy/common/download").param("fileName","../upload/keep.txt").param("delete","true")).andReturn().getResponse().getStatus());
        assertEquals("keep",Files.readString(root.resolve("upload/keep.txt")));
        Files.writeString(root.resolve("short.txt"),"short");
        assertEquals("short",mvc.perform(get("/package-legacy/profile/short.txt")).andReturn().getResponse().getContentAsString());
        Files.createDirectories(root.resolve("download"));Files.writeString(root.resolve("download/test.txt"),"remove");
        assertEquals("remove",mvc.perform(get("/package-legacy/common/download").param("fileName","test.txt").param("delete","true")).andReturn().getResponse().getContentAsString());
        assertFalse(Files.exists(root.resolve("download/test.txt")));
        assertThrows(java.io.IOException.class,()->controller.safe("..\\secret.txt"));
    }
    @Test void refusesSymbolicLinkOutsideConfiguredRoot() throws Exception {
        Path outside=Files.createTempDirectory("package-file-outside");
        try {
            try {Files.createSymbolicLink(root.resolve("escape"),outside);}
            catch(java.io.IOException|UnsupportedOperationException denied){Assumptions.abort("Host does not permit symlink creation: "+denied.getClass().getSimpleName());}
            assertThrows(java.io.IOException.class,()->new PackageFileController(root.toString()).safe("escape/secret.txt"));
        } finally {Files.deleteIfExists(root.resolve("escape"));Files.deleteIfExists(outside);}
    }
}
