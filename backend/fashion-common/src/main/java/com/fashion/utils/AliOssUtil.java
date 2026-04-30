package com.fashion.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWarDeployment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 上传文件
     */
    public String upload(String fileName, byte[] bytes) {

        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            // 上传文件
            ossClient.putObject(bucketName, fileName, new ByteArrayInputStream(bytes));

        } catch (OSSException e) {
            log.error("上传文件失败: {}", e.getMessage());

            System.out.println("Error Message:" + e.getErrorMessage());
            System.out.println("Error Code:" + e.getErrorCode());
            System.out.println("Request ID:" + e.getRequestId());
            System.out.println("Host ID:" + e.getHostId());
        }catch ( ClientException ce) {
            log.error("上传文件失败: {}", ce.getMessage());
            System.out.println("Error Message:" + ce.getMessage());
        }finally
         {
             if(ossClient != null){
                 ossClient.shutdown();
             }
        }

        //文件访问路径 = https://bucketName.endpoint/fileName
        StringBuilder  url = new StringBuilder();
        url.append("https://")
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(fileName);
        log.info("文件上传成功: {}", url.toString());
        return url.toString();
    }
}
