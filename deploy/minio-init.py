"""MinIO bucket bootstrap (03-data-layer-conventions.md). Idempotent.
Private buckets by default (no bucket policy attached -> no public listing).
analytics-raw carries the spec'd 90-day lifecycle; the other buckets are namespace
reservations whose expiry is declared by their owning feature before it ships.
SSE-S3 at rest is attempted per bucket; failure warns (KMS wiring is a release task).
Object paths never contain PII (enforced by the owning services, not paths here).
"""
import os
import sys

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError

ENDPOINT = os.environ.get("MINIO_ENDPOINT", "http://minio:9000")
ACCESS = os.environ["MINIO_ROOT_USER"]
SECRET = os.environ["MINIO_ROOT_PASSWORD"]

# bucket -> expiry days (None = namespace reservation, expiry declared by owning feature)
BUCKETS: dict[str, int | None] = {
    "analytics-raw": 90,  # raw beacon NDJSON, lifecycle 90 days (spec 03)
    "resume-archives": None,
    "avatars": None,
    "link-hits": None,
}


def main() -> int:
    s3 = boto3.client(
        "s3",
        endpoint_url=ENDPOINT,
        aws_access_key_id=ACCESS,
        aws_secret_access_key=SECRET,
        config=Config(signature_version="s3v4"),
        region_name="us-east-1",
    )
    for bucket, expiry in BUCKETS.items():
        try:
            s3.head_bucket(Bucket=bucket)
        except ClientError:
            s3.create_bucket(Bucket=bucket)
        rules = [
            {
                "ID": "abort-multipart",
                "Status": "Enabled",
                "Filter": {"Prefix": ""},
                "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 7},
            }
        ]
        if expiry is not None:
            rules.append(
                {
                    "ID": "ttl",
                    "Status": "Enabled",
                    "Filter": {"Prefix": ""},
                    "Expiration": {"Days": expiry},
                }
            )
        s3.put_bucket_lifecycle_configuration(
            Bucket=bucket, LifecycleConfiguration={"Rules": rules}
        )
        try:
            s3.put_bucket_encryption(
                Bucket=bucket,
                ServerSideEncryptionConfiguration={
                    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
                },
            )
        except ClientError as ex:
            print(f"WARN {bucket}: server-side encryption not applied ({ex})")
        print(f"ok {bucket} (expiry={expiry})")
    print("minio-init ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
