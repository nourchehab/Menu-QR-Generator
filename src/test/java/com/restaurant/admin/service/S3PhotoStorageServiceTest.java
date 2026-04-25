package com.restaurant.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3PhotoStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Utilities s3Utilities;

    @InjectMocks
    private S3PhotoStorageService s3PhotoStorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3PhotoStorageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3PhotoStorageService, "photosFolder", "test-photos");
        ReflectionTestUtils.setField(s3PhotoStorageService, "localPhotoDir", "test-uploads");
        ReflectionTestUtils.setField(s3PhotoStorageService, "localLogoDir", "test-uploads-logos");
    }

    @Test
    void testUploadNewPhotoSuccess() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/test-photos/random-uuid.jpg");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.utilities()).thenReturn(s3Utilities);
        when(s3Utilities.getUrl(any(GetUrlRequest.class))).thenReturn(expectedUrl);

        // Act
        String resultUrl = s3PhotoStorageService.uploadNewPhoto(file);

        // Assert
        assertNotNull(resultUrl);
        assertEquals(expectedUrl.toExternalForm(), resultUrl);
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadNewPhotoFailsWhenS3ClientThrowsException() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "test data".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        // Act
        String result = s3PhotoStorageService.uploadNewPhoto(file);

        // Assert: service should fall back to local storage and return a local uploads path
        assertNotNull(result);
        assertTrue(result.startsWith("/uploads/photos/"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadNewLogoFallsBackToLocalStorageWhenS3Fails() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "logo data".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").build());

        // Act
        String result = s3PhotoStorageService.uploadNewLogo(file);

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith("/uploads/logos/"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
