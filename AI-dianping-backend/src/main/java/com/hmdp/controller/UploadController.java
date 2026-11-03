package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/upload")
public class UploadController {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final Path imageRoot;
    private final IBlogService blogService;

    public UploadController(@Value("${hm.upload.path:./upload/}") String uploadPath, IBlogService blogService) {
        this.imageRoot = Path.of(uploadPath).resolve("imgs").toAbsolutePath().normalize();
        this.blogService = blogService;
    }

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) throws IOException {
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_BYTES) return Result.fail("请选择不超过10MB的图片");
        String extension;
        try (InputStream input = image.getInputStream()) {
            byte[] header = input.readNBytes(12);
            extension = imageType(header);
        }
        if (extension == null) return Result.fail("仅支持 JPG、PNG 或 WebP 图片");
        Long userId = UserHolder.getUser().getId();
        Path folder = imageRoot.resolve("blogs").resolve(userId.toString()).normalize();
        Files.createDirectories(folder);
        String relativeName = "/blogs/" + userId + "/" + UUID.randomUUID() + "." + extension;
        image.transferTo(imageRoot.resolve(relativeName.substring(1)));
        return Result.ok(relativeName);
    }

    @DeleteMapping("/blog")
    public Result deleteUnpublishedImage(@RequestParam("name") String name) throws IOException {
        Long userId = UserHolder.getUser().getId();
        String prefix = "/imgs/blogs/" + userId + "/";
        if (name == null || !name.startsWith(prefix)
                || !name.substring(prefix.length()).matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")) {
            return Result.fail("只能删除自己尚未发布的图片");
        }
        if (blogService.query().like("images", name).count() > 0) {
            return Result.fail("图片已用于发布的笔记，无法删除");
        }
        Path file = imageRoot.resolve(name.substring("/imgs/".length())).normalize();
        if (!file.startsWith(imageRoot)) return Result.fail("图片路径无效");
        Files.deleteIfExists(file);
        return Result.ok();
    }

    private String imageType(byte[] h) {
        if (h.length >= 3 && (h[0] & 255) == 0xff && (h[1] & 255) == 0xd8 && (h[2] & 255) == 0xff) return "jpg";
        if (h.length >= 8 && (h[0] & 255) == 0x89 && h[1] == 0x50 && h[2] == 0x4e && h[3] == 0x47
                && h[4] == 0x0d && h[5] == 0x0a && h[6] == 0x1a && h[7] == 0x0a) return "png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
