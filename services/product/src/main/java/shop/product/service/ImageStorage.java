package shop.product.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import shop.product.config.StorageProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// object-storage gateway for product images: bytes live in the S3/MinIO bucket while the catalog
// keeps only the key (and a derived public URL), so image bytes stay out of the API payload. Used
// by the catalog write path (createGood) and by the one-time legacy backfill.
@Service
public class ImageStorage {

  private final S3Client s3;
  private final StorageProperties props;

  public ImageStorage(S3Client s3, StorageProperties props) {
    this.s3 = s3;
    this.props = props;
  }

  // stores the bytes under a fresh random key and returns it. The Content-Type is sniffed from the
  // magic bytes so the browser <img> gets a sensible type; the upload boundary and legacy bytea
  // column both dropped the original MIME type.
  public String put(byte[] bytes) {
    String key = UUID.randomUUID().toString();
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(props.bucket())
            .key(key)
            .contentType(contentType(bytes))
            .build(),
        RequestBody.fromBytes(bytes));
    return key;
  }

  public byte[] get(String key) {
    ResponseBytes<GetObjectResponse> object =
        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(props.bucket()).key(key).build());
    return object.asByteArray();
  }

  public void delete(String key) {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
  }

  // the browser-reachable URL for a stored key (the bucket is public-read, so no signing needed)
  public String urlFor(String key) {
    return props.publicBaseUrl() + "/" + key;
  }

  // minimal magic-byte sniff so the stored object carries a sensible Content-Type for the browser
  private static String contentType(byte[] b) {
    if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
      return "image/png";
    }
    if (b.length >= 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
      return "image/gif";
    }
    if (b.length >= 12
        && b[0] == 'R'
        && b[1] == 'I'
        && b[2] == 'F'
        && b[3] == 'F'
        && b[8] == 'W'
        && b[9] == 'E'
        && b[10] == 'B'
        && b[11] == 'P') {
      return "image/webp";
    }
    return "application/octet-stream";
  }
}
