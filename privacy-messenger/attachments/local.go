package attachments

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

type LocalStore struct {
	baseDir string
	baseURL string
}

func NewLocalStore(baseDir, baseURL string) (*LocalStore, error) {
	if err := os.MkdirAll(baseDir, 0755); err != nil {
		return nil, fmt.Errorf("could not create attachments directory: %w", err)
	}
	return &LocalStore{
		baseDir: baseDir,
		baseURL: baseURL,
	}, nil
}

func (s *LocalStore) SaveFile(r io.Reader) (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	id := hex.EncodeToString(bytes)
	path := filepath.Join(s.baseDir, id)

	out, err := os.Create(path)
	if err != nil {
		return "", err
	}
	defer out.Close()

	if _, err := io.Copy(out, r); err != nil {
		return "", err
	}

	return fmt.Sprintf("%s/v1/attachments/download/%s", s.baseURL, id), nil
}

func (s *LocalStore) GetFilePath(id string) string {
	return filepath.Join(s.baseDir, id)
}
