package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"privacy-messenger/config"
)

// twilioSMSSender implements auth.SMSSender against Twilio's Messages API.
// Any other provider can be swapped in by implementing the same interface —
// nothing else in the codebase depends on Twilio specifically.
type twilioSMSSender struct {
	accountSID string
	authToken  string
	fromNumber string
	httpClient *http.Client
}

func newSMSSender(cfg *config.Config) (*twilioSMSSender, error) {
	fromNumber := os.Getenv("SMS_FROM_NUMBER")
	if fromNumber == "" {
		return nil, errors.New("SMS_FROM_NUMBER environment variable is required")
	}
	if cfg.SMSProviderAPIKey == "" || cfg.SMSProviderSecret == "" {
		return nil, errors.New("SMS provider credentials are not configured")
	}
	return &twilioSMSSender{
		accountSID: cfg.SMSProviderAPIKey,
		authToken:  cfg.SMSProviderSecret,
		fromNumber: fromNumber,
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}, nil
}

func (s *twilioSMSSender) SendSMS(ctx context.Context, phoneNumber, message string) error {
	endpoint := fmt.Sprintf("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", s.accountSID)

	form := url.Values{}
	form.Set("To", phoneNumber)
	form.Set("From", s.fromNumber)
	form.Set("Body", message)

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader(form.Encode()))
	if err != nil {
		return fmt.Errorf("building sms request: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.SetBasicAuth(s.accountSID, s.authToken)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("sending sms: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		// Deliberately not including the response body in the error: it
		// may echo back the phone number or message content, and errors
		// can end up in logs.
		return fmt.Errorf("sms provider returned status %d", resp.StatusCode)
	}
	return nil
}
