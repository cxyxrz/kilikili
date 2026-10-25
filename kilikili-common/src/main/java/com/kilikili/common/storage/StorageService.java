package com.kilikili.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import com.kilikili.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private final MinioClient client;
    private final String bucket;

    public StorageService(@Value("${kilikili.storage.endpoint}") String endpoint,
                          @Value("${kilikili.storage.access-key}") String accessKey,
                          @Value("${kilikili.storage.secret-key}") String secretKey,
                          @Value("${kilikili.storage.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    public String uploadVideo(MultipartFile file, long userId) {
        try {
            ensureBucket();
            String suffix = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.')) : ".mp4";
            String objectName = "videos/" + userId + "/" + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType()).build());
            log.info("视频对象上传成功: userId={}, objectKey={}, size={}", userId, objectName, file.getSize());
            return objectName;
        } catch (Exception exception) {
            log.error("视频对象上传失败: userId={}, fileName={}", userId, file.getOriginalFilename(), exception);
            throw new IllegalStateException("视频存储失败", exception);
        }
    }

    public String uploadAvatar(MultipartFile file, long userId) {
        try {
            if (file.isEmpty()) throw new BusinessException("头像文件不能为空");
            if (file.getSize() > 5 * 1024 * 1024) throw new BusinessException("头像大小不能超过 5MB");
            String contentType = file.getContentType();
            if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/webp"))) {
                throw new BusinessException("头像仅支持 JPG、PNG 或 WebP 格式");
            }
            ensureBucket();
            String suffix = contentType.equals("image/png") ? ".png" : contentType.equals("image/webp") ? ".webp" : ".jpg";
            String objectName = "avatars/" + userId + "/" + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(file.getInputStream(), file.getSize(), -1).contentType(contentType).build());
            log.info("头像对象上传成功: userId={}, objectKey={}, size={}", userId, objectName, file.getSize());
            return objectName;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("头像对象上传失败: userId={}, fileName={}", userId, file.getOriginalFilename(), exception);
            throw new IllegalStateException("头像存储失败", exception);
        }
    }

    public String uploadCover(MultipartFile file, long userId) {
        try {
            if (file.isEmpty()) throw new BusinessException("封面文件不能为空");
            if (file.getSize() > 10 * 1024 * 1024) throw new BusinessException("封面大小不能超过 10MB");
            String contentType = file.getContentType();
            if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/webp"))) throw new BusinessException("封面仅支持 JPG、PNG 或 WebP 格式");
            ensureBucket();
            String suffix = contentType.equals("image/png") ? ".png" : contentType.equals("image/webp") ? ".webp" : ".jpg";
            String objectName = "covers/" + userId + "/" + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName).stream(file.getInputStream(), file.getSize(), -1).contentType(contentType).build());
            log.info("视频封面上传成功: userId={}, objectKey={}", userId, objectName);
            return objectName;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("视频封面上传失败: userId={}, fileName={}", userId, file.getOriginalFilename(), exception);
            throw new IllegalStateException("封面存储失败", exception);
        }
    }

    public String presignedReadUrl(String objectName) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket).object(objectName).method(Method.GET).expiry(3600).build());
        } catch (Exception exception) {
            log.error("生成视频播放地址失败: objectKey={}", objectName, exception);
            throw new IllegalStateException("生成播放地址失败", exception);
        }
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception exception) {
            throw new IllegalStateException("存储桶初始化失败", exception);
        }
    }

    public String uploadChunk(String baseObjectKey, int partNumber, MultipartFile chunk) {
        try {
            ensureBucket();
            String partKey = baseObjectKey + "/parts/" + partNumber;
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(partKey)
                    .stream(chunk.getInputStream(), chunk.getSize(), -1)
                    .contentType("application/octet-stream")
                    .build());
            log.info("分片上传成功: partKey={}, part={}, size={}", partKey, partNumber, chunk.getSize());
            return partKey;
        } catch (Exception exception) {
            log.error("分片上传失败: baseKey={}, part={}", baseObjectKey, partNumber, exception);
            throw new IllegalStateException("分片上传失败", exception);
        }
    }

    public void composeChunks(String targetObjectKey, List<String> partKeys, String contentType) {
        try {
            List<ComposeSource> sources = new ArrayList<>();
            for (String partKey : partKeys) {
                sources.add(ComposeSource.builder().bucket(bucket).object(partKey).build());
            }
            client.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetObjectKey)
                    .sources(sources)
                    .userMetadata(java.util.Map.of("Content-Type", contentType))
                    .build());
            log.info("分片合并完成: targetKey={}, parts={}", targetObjectKey, partKeys.size());
            for (String partKey : partKeys) {
                try {
                    client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(partKey).build());
                } catch (Exception e) {
                    log.warn("分片清理失败(可忽略): partKey={}", partKey, e);
                }
            }
        } catch (Exception exception) {
            log.error("分片合并失败: targetKey={}", targetObjectKey, exception);
            throw new IllegalStateException("分片合并失败", exception);
        }
    }

    public String generateVideoObjectKey(long userId, String filename) {
        String suffix = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.')) : ".mp4";
        return "videos/" + userId + "/" + UUID.randomUUID() + suffix;
    }

    public void downloadObject(String objectKey, Path target) {
        try {
            try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            log.error("对象下载失败: objectKey={}", objectKey, exception);
            throw new IllegalStateException("对象下载失败", exception);
        }
    }

    public void uploadLocalFile(String objectKey, Path file, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(Files.newInputStream(file), Files.size(file), -1)
                    .contentType(contentType).build());
        } catch (Exception exception) {
            log.error("本地文件上传失败: objectKey={}, file={}", objectKey, file, exception);
            throw new IllegalStateException("对象上传失败", exception);
        }
    }

    public String readTextObject(String objectKey) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.error("对象读取失败: objectKey={}", objectKey, exception);
            throw new IllegalStateException("对象读取失败", exception);
        }
    }
}