package common

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"

	"golang.org/x/crypto/bcrypt"
)

func GenerateHMACWithKey(key []byte, data string) string {
	h := hmac.New(sha256.New, key)
	h.Write([]byte(data))
	return hex.EncodeToString(h.Sum(nil))
}

func GenerateHMAC(data string) string {
	h := hmac.New(sha256.New, []byte(CryptoSecret))
	h.Write([]byte(data))
	return hex.EncodeToString(h.Sum(nil))
}

func Password2Hash(password string) (string, error) {
	passwordBytes := []byte(password)
	hashedPassword, err := bcrypt.GenerateFromPassword(passwordBytes, bcrypt.DefaultCost)
	return string(hashedPassword), err
}

func ValidatePasswordAndHash(password string, hash string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}

// ---- AES-256-GCM encryption for sensitive fields at rest (channel keys, etc.) ----

const aesPrefix = "AES256:"

// deriveAESKey derives a 32-byte AES-256 key from CryptoSecret.
func deriveAESKey() []byte {
	h := sha256.Sum256([]byte(CryptoSecret))
	return h[:]
}

// EncryptByAES encrypts plaintext with AES-256-GCM and returns a prefixed,
// base64-encoded string suitable for database storage.
func EncryptByAES(plaintext string) (string, error) {
	if plaintext == "" {
		return "", nil
	}
	block, err := aes.NewCipher(deriveAESKey())
	if err != nil {
		return "", fmt.Errorf("aes.NewCipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("cipher.NewGCM: %w", err)
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", fmt.Errorf("read nonce: %w", err)
	}
	ciphertext := gcm.Seal(nil, nonce, []byte(plaintext), nil)
	enc := base64.StdEncoding.EncodeToString(nonce) + ":" + base64.StdEncoding.EncodeToString(ciphertext)
	return aesPrefix + enc, nil
}

// DecryptByAES decrypts a value produced by EncryptByAES. If the value does
// not have the AES256: prefix it is returned as-is (plaintext backwards
// compatibility for pre-encryption data).
func DecryptByAES(stored string) (string, error) {
	if stored == "" {
		return "", nil
	}
	if len(stored) < len(aesPrefix) || stored[:len(aesPrefix)] != aesPrefix {
		// Not encrypted — plaintext backwards compat
		return stored, nil
	}
	payload := stored[len(aesPrefix):]
	parts := splitN(payload, ":", 2)
	if len(parts) != 2 {
		return stored, nil // malformed, return as-is
	}
	nonce, err := base64.StdEncoding.DecodeString(parts[0])
	if err != nil {
		return stored, nil
	}
	ciphertext, err := base64.StdEncoding.DecodeString(parts[1])
	if err != nil {
		return stored, nil
	}
	block, err := aes.NewCipher(deriveAESKey())
	if err != nil {
		return "", fmt.Errorf("aes.NewCipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("cipher.NewGCM: %w", err)
	}
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		// Decryption failed — key may have changed. Return the stored value
		// so the caller can detect the mismatch.
		return stored, fmt.Errorf("gcm.Open: %w", err)
	}
	return string(plaintext), nil
}

// splitN splits s by sep into at most n parts. Hand written to avoid importing strings.
func splitN(s, sep string, n int) []string {
	result := make([]string, 0, n)
	for i := 0; i < n-1; i++ {
		idx := indexOf(s, sep)
		if idx < 0 {
			result = append(result, s)
			return result
		}
		result = append(result, s[:idx])
		s = s[idx+len(sep):]
	}
	result = append(result, s)
	return result
}

func indexOf(s, sep string) int {
	for i := 0; i+len(sep) <= len(s); i++ {
		if s[i:i+len(sep)] == sep {
			return i
		}
	}
	return -1
}
