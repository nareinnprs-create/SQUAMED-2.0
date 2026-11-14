package com.squamed.core.patient.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.util.UUID

/**
 * Patient photo object storage (00.07): S3 API — MinIO in dev (compose `pgsty/silo`), S3 in prod.
 * Disabled (photos.enabled=false) falls back to 503 so the API contract is honest instead of
 * silently dropping bytes.
 */
@Service
class PatientPhotoStorage(
  @Value("\${squamed.photos.enabled:true}") private val enabled: Boolean,
  @Value("\${squamed.photos.endpoint:http://localhost:9000}") private val endpoint: String,
  @Value("\${squamed.photos.region:us-east-1}") private val region: String,
  @Value("\${squamed.photos.access-key:squamed}") private val accessKey: String,
  @Value("\${squamed.photos.secret-key:squamed_dev_password}") private val secretKey: String,
  @Value("\${squamed.photos.bucket:squamed-patient-photos}") private val bucket: String,
  @Value("\${squamed.photos.path-style:true}") private val pathStyle: Boolean,
) {
  private val logger = LoggerFactory.getLogger(PatientPhotoStorage::class.java)
  private lateinit var s3: S3Client

  companion object {
    val ALLOWED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    const val MAX_BYTES = 5L * 1024 * 1024
  }

  @PostConstruct
  fun init() {
    if (!enabled) {
      logger.warn("Patient photo storage disabled (squamed.photos.enabled=false)")
      return
    }
    val builder =
      S3Client
        .builder()
        .region(Region.of(region))
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
    if (pathStyle) builder.serviceConfiguration { it.pathStyleAccessEnabled(true) }
    s3 = builder.build()
    val exists =
      try {
        s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
        true
      } catch (e: Exception) {
        false
      }
    if (!exists) {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
      logger.info("Created patient photo bucket {}", bucket)
    }
  }

  fun upload(
    hospitalId: UUID,
    patientId: UUID,
    file: MultipartFile,
  ): String {
    assertEnabled()
    val contentType = file.contentType ?: ""
    if (contentType !in ALLOWED_CONTENT_TYPES) throw IllegalArgumentException("PHOTO_CONTENT_TYPE_UNSUPPORTED")
    val bytes = file.bytes
    if (bytes.isEmpty()) throw IllegalArgumentException("PHOTO_EMPTY")
    if (bytes.size > MAX_BYTES) throw IllegalArgumentException("PHOTO_TOO_LARGE")
    val ext = contentType.substringAfterLast("/").replace("jpeg", "jpg")
    val key = "photos/$hospitalId/$patientId/${UUID.randomUUID()}.$ext"
    s3.putObject(
      PutObjectRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .build(),
      RequestBody.fromBytes(bytes),
    )
    return key
  }

  fun download(key: String): Pair<ByteArray, String> {
    assertEnabled()
    val obj =
      s3.getObject(
        GetObjectRequest
          .builder()
          .bucket(bucket)
          .key(key)
          .build(),
      )
    return obj.readAllBytes() to obj.response().contentType()
  }

  private fun assertEnabled() {
    if (!enabled) throw IllegalStateException("PHOTO_STORAGE_DISABLED")
  }
}
