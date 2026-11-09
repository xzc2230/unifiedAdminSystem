package org.jeecg.modules.packagemanagement.controller.legacy;

import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/package-legacy")
public class PackageFileController {
    private final Path root;
    private static final Set<String> ALLOWED=Set.of("bmp","gif","jpg","jpeg","png","doc","docx","xls","xlsx","ppt","pptx","html","htm","txt","rar","zip","gz","bz2","mp4","avi","rmvb","webp","pdf");
    public PackageFileController(@Value("${jeecg.package-management.profile-root:${jeecg.path.upload}}") String directory) throws IOException {
        root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);
    }
    public Path safe(String relative) throws IOException {
        if(relative==null||relative.contains("\\")||relative.indexOf('\0')>=0)throw new IOException("资源路径无效");
        Path target=root.resolve(relative).normalize();
        if(!target.startsWith(root)||target.equals(root))throw new IOException("资源路径越界");
        Path existing=target;
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
        if(!existing.toRealPath().startsWith(root.toRealPath()))throw new IOException("资源符号链接越界");
        return target;
    }
    private String store(MultipartFile file) throws IOException {
        String name=file.getOriginalFilename();
        if(name==null||name.length()>100||file.isEmpty()||file.getSize()>50L*1024*1024)throw new IOException("文件名或大小不符合要求");
        String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(!ALLOWED.contains(extension))throw new IOException("不支持的文件类型");
        String relative="upload/"+LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString().replace('-','/')+"/"+UUID.randomUUID()+"."+extension;
        Path target=safe(relative);Files.createDirectories(target.getParent());
        // Recheck after directory creation, and refuse to follow an existing final symlink.
        target=safe(relative);try(var input=file.getInputStream();var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){input.transferTo(out);}
        return "/profile/"+relative;
    }
    private String base(HttpServletRequest request) {
        int port=request.getServerPort();
        return request.getScheme()+"://"+request.getServerName()+((port==80||port==443)?"":":"+port);
    }
    @PostMapping("/common/upload") public LegacyAjaxResult upload(@RequestParam MultipartFile file,HttpServletRequest request) {
        try {String name=store(file);var result=LegacyAjaxResult.success();
            result.put("url",base(request)+name);result.put("fileName",name);
            result.put("newFileName",Path.of(name).getFileName().toString());result.put("originalFilename",file.getOriginalFilename());return result;
        }catch(IOException failure){return LegacyAjaxResult.error(failure.getMessage());}
    }
    @PostMapping("/common/uploads") public LegacyAjaxResult uploads(@RequestParam List<MultipartFile> files,HttpServletRequest request) {
        List<String> urls=new ArrayList<>(),names=new ArrayList<>(),newNames=new ArrayList<>(),original=new ArrayList<>();
        try {for(var file:files){String name=store(file);names.add(name);urls.add(base(request)+name);newNames.add(Path.of(name).getFileName().toString());original.add(file.getOriginalFilename());}
            var result=LegacyAjaxResult.success();result.put("urls",String.join(",",urls));result.put("fileNames",String.join(",",names));
            result.put("newFileNames",String.join(",",newNames));result.put("originalFilenames",String.join(",",original));return result;
        }catch(IOException failure){return LegacyAjaxResult.error(failure.getMessage());}
    }
    private void send(Path file,String name,boolean attachment,HttpServletResponse response) throws IOException {
        if(!Files.isRegularFile(file)) {response.sendError(404);return;}
        response.setContentType(attachment?"application/octet-stream":Objects.requireNonNullElse(Files.probeContentType(file),"application/octet-stream"));
        if(attachment)response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(name,java.nio.charset.StandardCharsets.UTF_8).build().toString());
        try(var input=Files.newInputStream(file)){input.transferTo(response.getOutputStream());}
    }
    private Path downloadFile(String name) throws IOException {
        if(name==null||name.isBlank()||name.contains("..")||name.contains("/")||name.contains("\\")||name.contains(":"))
            throw new IOException("下载文件名无效");
        Path file=safe("download/"+name);
        Path directory=root.resolve("download");
        if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS)&&!directory.toRealPath().equals(root.toRealPath().resolve("download")))
            throw new IOException("下载目录不可重定向");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)&&!file.toRealPath().startsWith(directory.toRealPath()))
            throw new IOException("下载资源越界");
        return file;
    }
    @GetMapping("/common/download") public void download(@RequestParam String fileName,@RequestParam(defaultValue="false") boolean delete,HttpServletResponse response) throws IOException {
        try {Path file=downloadFile(fileName);send(file,System.currentTimeMillis()+fileName.substring(fileName.indexOf('_')+1),true,response);if(delete&&Files.exists(file))Files.delete(file);}
        catch(IOException failure){response.sendError(400);}
    }
    @GetMapping("/common/download/resource") public void resource(@RequestParam String resource,HttpServletResponse response) throws IOException {
        try {if(!resource.startsWith("/profile/"))throw new IOException("路径无效");Path file=safe(resource.substring(9));send(file,file.getFileName().toString(),true,response);}
        catch(IOException failure){response.sendError(400);}
    }
    @GetMapping({"/profile/{file}","/profile/{dir}/{file}","/profile/**"}) public void profile(HttpServletRequest request,HttpServletResponse response) throws IOException {
        try {String prefix=request.getContextPath()+"/package-legacy/profile/";String name=request.getRequestURI().substring(prefix.length());
            Path file=safe(java.net.URLDecoder.decode(name,java.nio.charset.StandardCharsets.UTF_8));send(file,file.getFileName().toString(),false,response);}
        catch(IOException|IllegalArgumentException failure){response.sendError(400);}
    }
}
