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
 * Patient document object storage (01.05): mirrors PatientPhotoStorage — S3 API, MinIO in dev,
 * S3 in prod. Disabled (documents.enabled=false) fails with 503 so the API contract stays honest.
 */
@Service
class PatientDocumentStorage(
  @Value("\${squamed.documents.enabled:true}") private val enabled: Boolean,
  @Value("\${squamed.documents.endpoint:http://localhost:9000}") private val endpoint: String,
  @Value("\${squamed.documents.region:us-east-1}") private val region: String,
  @Value("\${squamed.documents.access-key:squamed}") private val accessKey: String,
  @Value("\${squamed.documents.secret-key:squamed_dev_password}") private val secretKey: String,
  @Value("\${squamed.documents.bucket:squamed-patient-documents}") private val bucket: String,
  @Value("\${squamed.documents.path-style:true}") private val pathStyle: Boolean,
) {
  private val logger = LoggerFactory.getLogger(PatientDocumentStorage::class.java)
  private lateinit var s3: S3Client

  companion object {
    val ALLOWED_CONTENT_TYPES =
      setOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
      )
    const val MAX_BYTES = 20L * 1024 * 1024
  }

  @PostConstruct
  fun init() {
    if (!enabled) {
      logger.warn("Patient document storage disabled (squamed.documents.enabled=false)")
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
      logger.info("Created patient document bucket {}", bucket)
    }
  }

  fun upload(
    hospitalId: UUID,
    patientId: UUID,
    file: MultipartFile,
  ): String {
    assertEnabled()
    val contentType = file.contentType ?: ""
    if (contentType !in ALLOWED_CONTENT_TYPES) throw IllegalArgumentException("DOCUMENT_CONTENT_TYPE_UNSUPPORTED")
    val bytes = file.bytes
    if (bytes.isEmpty()) throw IllegalArgumentException("DOCUMENT_EMPTY")
    if (bytes.size > MAX_BYTES) throw IllegalArgumentException("DOCUMENT_TOO_LARGE")
    val ext = contentType.substringAfterLast("/").replace("jpeg", "jpg")
    val key = "documents/$hospitalId/$patientId/${UUID.randomUUID()}.$ext"
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
    if (!enabled) throw IllegalStateException("DOCUMENT_STORAGE_DISABLED")
  }
}
