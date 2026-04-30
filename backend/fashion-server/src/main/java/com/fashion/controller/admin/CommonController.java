package com.fashion.controller.admin;

import com.fashion.result.Result;
import com.fashion.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping
@Slf4j
public class CommonController {
    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 上传文件（图片之类）
     */
    @RequestMapping("/upload/oss")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("上传文件: {}", file);

        String originalFilename = file.getOriginalFilename();
        String fileName = originalFilename.substring(originalFilename.lastIndexOf("."));
        String filePath = UUID.randomUUID().toString() + fileName;

        try {
            String url =aliOssUtil.upload(filePath, file.getBytes());
            return Result.success(url);
        } catch (IOException e) {
            log.error("上传文件失败", e);
        }
        
        return Result.error("上传失败");

    }
}
