package com.kilikili.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import com.kilikili.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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
}
