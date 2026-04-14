package com.restaurant.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Configuration
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.region:eu-north-1}")
    private String region;

    @Value("${aws.s3.bucket-name:}")
    private String bucketName;

    @Value("${aws.access.key:}")
    private String accessKey;

    @Value("${aws.secret.key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3Client for region: {}, bucket: {}", region, bucketName);
        
        var builder = S3Client.builder().region(Region.of(region));
        
        // Use explicit credentials if available, otherwise use DefaultCredentialsProvider
        if (isNotEmpty(accessKey) && isNotEmpty(secretKey)) {
            log.info("Using AWS credentials from environment variables");
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ));
        } else {
            log.info("Using DefaultCredentialsProvider (IAM role, ~/.aws/credentials, or environment)");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        
        S3Client client = builder.build();
        
        // Validate S3 bucket is accessible
        validateS3Bucket(client);
        
        return client;
    }

    private void validateS3Bucket(S3Client client) {
        if (isNotEmpty(bucketName)) {
            try {
                HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucketName).build();
                client.headBucket(request);
                log.info("✓ S3 bucket '{}' is accessible", bucketName);
            } catch (Exception e) {
                log.error("✗ Failed to access S3 bucket '{}': {}", bucketName, e.getMessage());
                log.warn("Logos will fail to upload without a valid S3 bucket. Please configure AWS credentials and bucket.");
            }
        } else {
            log.warn("AWS S3 bucket name not configured. Set aws.s3.bucket-name property.");
        }
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
