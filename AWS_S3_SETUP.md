# AWS S3 Configuration Guide for Logo Storage

## Overview
Logos are now stored exclusively in AWS S3. This guide walks you through the setup process.

## Prerequisites
- AWS Account
- S3 bucket created (e.g., `restaurant-logos-bucket`)
- AWS IAM credentials or IAM role

## Configuration Options

### Option 1: Environment Variables (Recommended for Production)

Set these environment variables before running the application:

```bash
# AWS Credentials
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"

# S3 Configuration
export AWS_S3_BUCKET_NAME="your-bucket-name"
export AWS_S3_REGION="eu-north-1"  # Optional, defaults to eu-north-1
```

**On Railway (Production):**
1. Go to your Railway project
2. Click Variables
3. Add the above variables
4. Deploy

### Option 2: AWS Credentials File (Development)

On your local machine, create `~/.aws/credentials`:

```ini
[default]
aws_access_key_id = your-access-key
aws_secret_access_key = your-secret-key
```

And `~/.aws/config`:

```ini
[default]
region = eu-north-1
```

Then set:
```bash
export AWS_S3_BUCKET_NAME="your-bucket-name"
```

### Option 3: IAM Role (Recommended for AWS EC2/ECS/Lambda)

If running on AWS infrastructure, use an IAM role attached to the instance/container with S3 permissions.

## S3 Bucket Permissions

Your S3 bucket needs the following IAM policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:HeadObject"
      ],
      "Resource": "arn:aws:s3:::your-bucket-name/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:HeadBucket"
      ],
      "Resource": "arn:aws:s3:::your-bucket-name"
    }
  ]
}
```

## Creating an S3 Bucket

```bash
aws s3 mb s3://restaurant-logos-bucket --region eu-north-1
```

Optional: Make it public read (if you want public URLs without presigned URLs):

```bash
# Enable public access
aws s3api put-bucket-acl --bucket restaurant-logos-bucket --acl public-read

# Set bucket policy
aws s3api put-bucket-policy --bucket restaurant-logos-bucket --policy '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::restaurant-logos-bucket/*"
    }
  ]
}'
```

## Application Configuration

The application properties already include S3 settings:

```properties
# AWS S3 Configuration
aws.s3.bucket-name=${AWS_S3_BUCKET_NAME:restaurant-logos-bucket}
aws.s3.region=${AWS_S3_REGION:eu-north-1}
aws.s3.photos-folder=photos/menu-items
aws.s3.logos-folder=photos/logos
```

## Verification

When the application starts, you'll see logs like:

```
Initializing S3Client for region: eu-north-1, bucket: restaurant-logos-bucket
✓ S3 bucket 'restaurant-logos-bucket' is accessible
Successfully uploaded logo to S3: photos/logos/uuid-here.jpg
```

**If the bucket is not accessible, you'll see:**

```
✗ Failed to access S3 bucket: Access Denied
```

## Troubleshooting

### "S3 bucket name is not configured"
- **Solution**: Set `AWS_S3_BUCKET_NAME` environment variable

### "Failed to access S3 bucket: Access Denied"
- **Solution**: Check IAM credentials and bucket permissions

### "Failed to access S3 bucket: NoSuchBucket"
- **Solution**: Create the S3 bucket first

### "Failed to upload logo to S3"
- **Solution**: Check CloudWatch logs or check IAM permissions

## Important Notes

1. ⚠️ **Logos will NOT fall back to local storage** - they will fail if S3 is unavailable
2. Errors will be clearly logged with actionable messages
3. Menu items can fall back to local storage (for resilience), but logos cannot
4. All logos are stored with public-read ACL for direct URL access

## Cost Optimization

S3 pricing tips:
- Enable S3 Intelligent-Tiering for automatic cost optimization
- Enable lifecycle policies to delete old versions
- Use S3 Standard storage for frequently accessed files

## More Information

- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [AWS SDK for Java](https://docs.aws.amazon.com/sdk-for-java/)
