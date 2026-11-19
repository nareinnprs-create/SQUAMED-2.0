package com.squamed.core.laboratory.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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
 * Lab report object storage (02.04): mirrors PatientDocumentStorage — S3 API, MinIO in dev,
 * S3 in prod. Disabled (lab-reports.enabled=false) fails with 503 so the API contract stays honest.
 */
@Service
class LabReportStorage(
  @Value("\${squamed.lab-reports.enabled:true}") private val enabled: Boolean,
  @Value("\${squamed.lab-reports.endpoint:http://localhost:9000}") private val endpoint: String,
  @Value("\${squamed.lab-reports.region:us-east-1}") private val region: String,
  @Value("\${squamed.lab-reports.access-key:squamed}") private val accessKey: String,
  @Value("\${squamed.lab-reports.secret-key:squamed_dev_password}") private val secretKey: String,
  @Value("\${squamed.lab-reports.bucket:squamed-lab-reports}") private val bucket: String,
  @Value("\${squamed.lab-reports.path-style:true}") private val pathStyle: Boolean,
) {
  private val logger = LoggerFactory.getLogger(LabReportStorage::class.java)
  private lateinit var s3: S3Client

  @PostConstruct
  fun init() {
    if (!enabled) {
      logger.warn("Lab report storage disabled (squamed.lab-reports.enabled=false)")
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
      logger.info("Created lab report bucket {}", bucket)
    }
  }

  fun upload(
    hospitalId: UUID,
    orderId: UUID,
    version: Int,
    bytes: ByteArray,
  ): String {
    assertEnabled()
    val key = "lab-reports/$hospitalId/$orderId/v$version-${UUID.randomUUID()}.pdf"
    s3.putObject(
      PutObjectRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .contentType("application/pdf")
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
    return obj.readAllBytes() to "application/pdf"
  }

  private fun assertEnabled() {
    if (!enabled) throw IllegalStateException("LAB_REPORT_STORAGE_DISABLED")
  }
}
