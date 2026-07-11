package attachments

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/google/uuid"
)

// S3Store handles generating pre-signed URLs for media attachments.
// By using pre-signed URLs, the Go backend never proxies the heavy media
// bytes; the Android client encrypts the file locally and uploads directly
// to S3.
type S3Store struct {
	client     *s3.Client
	presign    *s3.PresignClient
	bucketName string
}

func NewS3Store(ctx context.Context, region, bucket, accessKey, secretKey string) (*S3Store, error) {
	cfg, err := config.LoadDefaultConfig(ctx,
		config.WithRegion(region),
		config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(accessKey, secretKey, "")),
	)
	if err != nil {
		return nil, fmt.Errorf("loading AWS config: %w", err)
	}

	client := s3.NewFromConfig(cfg)
	presign := s3.NewPresignClient(client)

	return &S3Store{
		client:     client,
		presign:    presign,
		bucketName: bucket,
	}, nil
}

// GenerateUploadURL creates a short-lived pre-signed PUT URL that the client
// can use to upload the encrypted attachment directly to S3.
// It returns the PUT URL and the corresponding GET URL the client should
// embed in the Double Ratchet envelope.
func (s *S3Store) GenerateUploadURL(ctx context.Context) (uploadURL, downloadURL string, err error) {
	objectKey := uuid.New().String()

	// 15 minutes is plenty for an upload link; we don't want these links
	// sitting around forever if unused.
	req, err := s.presign.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket: aws.String(s.bucketName),
		Key:    aws.String(objectKey),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = 15 * time.Minute
	})

	if err != nil {
		return "", "", fmt.Errorf("generating pre-signed url: %w", err)
	}

	uploadURL = req.URL
	
	// Assuming public read or pre-signed GET. For maximum security, the bucket
	// should be private, and we would generate a pre-signed GET URL here that
	// expires in e.g. 30 days. For simplicity of the MVP, we assume the bucket
	// allows public read of objects, since the objects are AES-256 encrypted anyway.
	// 
	// However, standard practice for private messengers is to also require an auth
	// token or presigned URL to download, to prevent unauthenticated scraping of ciphertexts.
	// Here we generate a long-lived GET URL (7 days max for S3 presigned URLs without IAM roles).
	getReq, err := s.presign.PresignGetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(s.bucketName),
		Key:    aws.String(objectKey),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = 7 * 24 * time.Hour
	})

	if err != nil {
		return "", "", fmt.Errorf("generating pre-signed download url: %w", err)
	}

	return uploadURL, getReq.URL, nil
}
