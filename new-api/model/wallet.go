package model

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"gorm.io/gorm"
)

// -- Domain errors --

var (
	ErrInsufficientBalance   = errors.New("insufficient balance")
	ErrIdempotencyConflict   = errors.New("idempotency conflict: same key, different request")
	ErrWalletNotFound        = errors.New("wallet not found")
	ErrTransferNotFound      = errors.New("transfer not found")
	ErrReverseAmountExceeded = errors.New("reverse amount exceeds original transfer")
	ErrAlreadyReleased       = errors.New("transfer already released")
	ErrWalletDisabled        = errors.New("wallet is disabled")
	ErrInvalidOwnerType      = errors.New("invalid owner type")
)

// -- Persistent models --

type WalletAccount struct {
	ID             uint   `gorm:"primaryKey"`
	OwnerType      string `gorm:"size:16;not null;uniqueIndex:uk_owner_currency"`
	OwnerID        string `gorm:"size:64;not null;uniqueIndex:uk_owner_currency"`
	Currency       string `gorm:"size:3;not null;default:CNY;uniqueIndex:uk_owner_currency"`
	AvailableCents int64  `gorm:"not null;default:0"`
	FrozenCents    int64  `gorm:"not null;default:0"`
	Status         string `gorm:"size:16;not null;default:ACTIVE"`
	RowVersion     int    `gorm:"not null;default:0"`
	CreatedAt      int64  `gorm:"autoCreateTime"`
	UpdatedAt      int64  `gorm:"autoUpdateTime"`
}

type WalletTransfer struct {
	ID               uint   `gorm:"primaryKey"`
	TransferNo       string `gorm:"size:64;not null;uniqueIndex"`
	BusinessType     string `gorm:"size:32;not null;index"`
	BusinessOrderNo  string `gorm:"size:64;not null;uniqueIndex:uk_business_transfer"`
	TransferType     string `gorm:"size:16;not null;uniqueIndex:uk_business_transfer"`
	IdempotencyKey   string `gorm:"size:128;not null"`
	FromOwnerType    string `gorm:"size:16"`
	FromOwnerID      string `gorm:"size:64"`
	ToOwnerType      string `gorm:"size:16"`
	ToOwnerID        string `gorm:"size:64"`
	AmountCents      int64  `gorm:"not null"`
	PlatformFeeCents int64  `gorm:"not null;default:0"`
	Currency         string `gorm:"size:3;not null;default:CNY"`
	Status           string `gorm:"size:16;not null;default:CREATED"`
	ReversedCents    int64  `gorm:"not null;default:0"`
	RequestHash      string `gorm:"size:64"`
	CreatedAt        int64  `gorm:"autoCreateTime"`
	UpdatedAt        int64  `gorm:"autoUpdateTime"`
}

type WalletLedgerEntry struct {
	ID           uint   `gorm:"primaryKey"`
	TransferNo   string `gorm:"size:64;not null;index"`
	OwnerType    string `gorm:"size:16;not null"`
	OwnerID      string `gorm:"size:64;not null"`
	EntryType    string `gorm:"size:6;not null"` // DEBIT or CREDIT
	AmountCents  int64  `gorm:"not null"`
	BalanceAfter int64  `gorm:"not null"`
	CreatedAt    int64  `gorm:"autoCreateTime"`
}

type WalletIdempotencyRecord struct {
	ID             uint   `gorm:"primaryKey"`
	Caller         string `gorm:"size:32;not null;index"`
	IdempotencyKey string `gorm:"size:128;not null;uniqueIndex:uk_caller_key"`
	RequestHash    string `gorm:"size:64;not null"`
	Status         string `gorm:"size:16;not null"`
	ResponseJSON   string `gorm:"type:text"`
	CreatedAt      int64  `gorm:"autoCreateTime"`
}

// -- Transfer result (returned to controller) --

type TransferResultData struct {
	TransferNo        string `json:"transfer_no"`
	Status            string `json:"status"`
	BuyerBalanceAfter int64  `json:"buyer_balance_after"`
}

type LedgerEntryView struct {
	TransferNo  string `json:"transfer_no"`
	EntryType   string `json:"entry_type"`
	AmountCents int64  `json:"amount_cents"`
	BalanceAfter int64 `json:"balance_after"`
	CreatedAt   int64  `json:"created_at"`
}

// -- Wallet operations --

func ensureWallet(tx *gorm.DB, ownerType, ownerID string) (*WalletAccount, error) {
	var wallet WalletAccount
	err := tx.Where("owner_type = ? AND owner_id = ?", ownerType, ownerID).First(&wallet).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		wallet = WalletAccount{
			OwnerType:      ownerType,
			OwnerID:        ownerID,
			Currency:       "CNY",
			AvailableCents: 0,
			FrozenCents:    0,
			Status:         "ACTIVE",
		}
		if err := tx.Create(&wallet).Error; err != nil {
			return nil, err
		}
	} else if err != nil {
		return nil, err
	}
	return &wallet, nil
}

func lockWallet(tx *gorm.DB, ownerType, ownerID string) (*WalletAccount, error) {
	var wallet WalletAccount
	err := tx.Set("gorm:query_option", "FOR UPDATE").
		Where("owner_type = ? AND owner_id = ?", ownerType, ownerID).
		First(&wallet).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		wallet = WalletAccount{
			OwnerType:      ownerType,
			OwnerID:        ownerID,
			Currency:       "CNY",
			AvailableCents: 0,
			FrozenCents:    0,
			Status:         "ACTIVE",
		}
		if err := tx.Create(&wallet).Error; err != nil {
			return nil, err
		}
		// Re-lock after create
		return lockWallet(tx, ownerType, ownerID)
	} else if err != nil {
		return nil, err
	}
	if wallet.Status != "ACTIVE" {
		return nil, ErrWalletDisabled
	}
	return &wallet, nil
}

func appendLedger(tx *gorm.DB, transferNo, ownerType, ownerID, entryType string, amount int64, balanceAfter int64) error {
	entry := WalletLedgerEntry{
		TransferNo:   transferNo,
		OwnerType:    ownerType,
		OwnerID:      ownerID,
		EntryType:    entryType,
		AmountCents:  amount,
		BalanceAfter: balanceAfter,
	}
	return tx.Create(&entry).Error
}

func hashRequest(req interface{}) string {
	// Simple hash — use json marshal for deterministic hashing
	data := fmt.Sprintf("%v", req)
	h := sha256.Sum256([]byte(data))
	return hex.EncodeToString(h[:])
}

// GetWalletBalance returns available and frozen balance.
func GetWalletBalance(ownerType, ownerID string) (available, frozen int64, err error) {
	wallet, err := ensureWallet(DB, ownerType, ownerID)
	if err != nil {
		return 0, 0, err
	}
	return wallet.AvailableCents, wallet.FrozenCents, nil
}

// PrecheckWallet validates wallet and balance.
func PrecheckWallet(ownerType, ownerID string, amountCents int64, permission string) error {
	wallet, err := ensureWallet(DB, ownerType, ownerID)
	if err != nil {
		return err
	}
	if wallet.Status != "ACTIVE" {
		return ErrWalletDisabled
	}
	if wallet.AvailableCents < amountCents {
		return ErrInsufficientBalance
	}
	return nil
}

// PurchaseTransferParams carries the purchase transfer request data.
type PurchaseTransferParams struct {
	BusinessOrderNo  string
	BuyerType        string
	BuyerID          string
	SellerType       string
	SellerID         string
	AmountCents      int64
	PlatformFeeCents int64
	Currency         string
	IdempotencyKey   string
}

// ExecutePurchaseTransfer performs atomic purchase: debit buyer, credit seller frozen, credit platform.
func ExecutePurchaseTransfer(params PurchaseTransferParams) (*TransferResultData, error) {
	tx := DB.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
		}
	}()

	// Lock buyer wallet and check balance
	buyerWallet, err := lockWallet(tx, params.BuyerType, params.BuyerID)
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	if buyerWallet.AvailableCents < params.AmountCents {
		tx.Rollback()
		return nil, ErrInsufficientBalance
	}

	// Debit buyer
	buyerWallet.AvailableCents -= params.AmountCents
	if err := tx.Save(buyerWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Credit seller frozen
	sellerWallet, err := lockWallet(tx, params.SellerType, params.SellerID)
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	sellerAmount := params.AmountCents - params.PlatformFeeCents
	sellerWallet.FrozenCents += sellerAmount
	if err := tx.Save(sellerWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Credit platform
	platformWallet, err := lockWallet(tx, "PLATFORM", "platform")
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	platformWallet.AvailableCents += params.PlatformFeeCents
	if err := tx.Save(platformWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Create transfer record
	transferNo := fmt.Sprintf("WT-%d", time.Now().UnixNano())
	transfer := WalletTransfer{
		TransferNo:       transferNo,
		BusinessType:     "PURCHASE",
		BusinessOrderNo:  params.BusinessOrderNo,
		TransferType:     "PURCHASE",
		IdempotencyKey:   params.IdempotencyKey,
		FromOwnerType:    params.BuyerType,
		FromOwnerID:      params.BuyerID,
		ToOwnerType:      params.SellerType,
		ToOwnerID:        params.SellerID,
		AmountCents:      params.AmountCents,
		PlatformFeeCents: params.PlatformFeeCents,
		Currency:         params.Currency,
		Status:           "SUCCEEDED",
	}
	if err := tx.Create(&transfer).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Append ledger entries (double-entry balanced)
	appendLedger(tx, transferNo, params.BuyerType, params.BuyerID, "DEBIT", params.AmountCents, buyerWallet.AvailableCents)
	appendLedger(tx, transferNo, params.SellerType, params.SellerID, "CREDIT", sellerAmount, sellerWallet.FrozenCents)
	appendLedger(tx, transferNo, "PLATFORM", "platform", "CREDIT", params.PlatformFeeCents, platformWallet.AvailableCents)

	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	return &TransferResultData{
		TransferNo:        transferNo,
		Status:            "SUCCEEDED",
		BuyerBalanceAfter: buyerWallet.AvailableCents,
	}, nil
}

// FindTransferByBusinessOrder looks up a transfer by business order number.
func FindTransferByBusinessOrder(orderNo string) (*WalletTransfer, error) {
	var transfer WalletTransfer
	err := DB.Where("business_order_no = ?", orderNo).First(&transfer).Error
	if err != nil {
		return nil, ErrTransferNotFound
	}
	return &transfer, nil
}

// ReleaseTransfer moves seller frozen funds to available.
func ReleaseTransfer(transferNo, idempotencyKey string) (*WalletTransfer, error) {
	tx := DB.Begin()

	var transfer WalletTransfer
	if err := tx.Where("transfer_no = ?", transferNo).First(&transfer).Error; err != nil {
		tx.Rollback()
		return nil, ErrTransferNotFound
	}
	if transfer.Status == "RELEASED" {
		tx.Rollback()
		return &transfer, nil // idempotent
	}

	// Move seller frozen → available
	sellerWallet, err := lockWallet(tx, transfer.ToOwnerType, transfer.ToOwnerID)
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	sellerAmount := transfer.AmountCents - transfer.PlatformFeeCents
	sellerWallet.FrozenCents -= sellerAmount
	sellerWallet.AvailableCents += sellerAmount
	if err := tx.Save(sellerWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Append ledger entries
	appendLedger(tx, transferNo, transfer.ToOwnerType, transfer.ToOwnerID, "DEBIT", sellerAmount, sellerWallet.FrozenCents)
	appendLedger(tx, transferNo, transfer.ToOwnerType, transfer.ToOwnerID, "CREDIT", sellerAmount, sellerWallet.AvailableCents)

	transfer.Status = "RELEASED"
	if err := tx.Save(&transfer).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		return nil, err
	}
	return &transfer, nil
}

// ReverseTransfer reverses (refunds) a transfer partially or fully.
func ReverseTransfer(transferNo string, amountCents int64, idempotencyKey string) (*WalletTransfer, error) {
	tx := DB.Begin()

	var transfer WalletTransfer
	if err := tx.Where("transfer_no = ?", transferNo).First(&transfer).Error; err != nil {
		tx.Rollback()
		return nil, ErrTransferNotFound
	}

	// Check cumulative reverse doesn't exceed original
	maxReverse := transfer.AmountCents - transfer.ReversedCents
	if amountCents > maxReverse {
		tx.Rollback()
		return nil, ErrReverseAmountExceeded
	}

	// Refund buyer
	buyerWallet, err := lockWallet(tx, transfer.FromOwnerType, transfer.FromOwnerID)
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	buyerWallet.AvailableCents += amountCents
	if err := tx.Save(buyerWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	// Reverse from seller first (frozen), then platform fee proportionally
	sellerFraction := amountCents * (transfer.AmountCents - transfer.PlatformFeeCents) / transfer.AmountCents
	platformFraction := amountCents - sellerFraction

	sellerWallet, err := lockWallet(tx, transfer.ToOwnerType, transfer.ToOwnerID)
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	if sellerWallet.FrozenCents >= sellerFraction {
		sellerWallet.FrozenCents -= sellerFraction
	} else {
		remaining := sellerFraction - sellerWallet.FrozenCents
		sellerWallet.FrozenCents = 0
		sellerWallet.AvailableCents -= remaining
	}
	if err := tx.Save(sellerWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	platformWallet, err := lockWallet(tx, "PLATFORM", "platform")
	if err != nil {
		tx.Rollback()
		return nil, err
	}
	platformWallet.AvailableCents -= platformFraction
	if err := tx.Save(platformWallet).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	transfer.ReversedCents += amountCents
	if transfer.ReversedCents >= transfer.AmountCents {
		transfer.Status = "REVERSED"
	} else {
		transfer.Status = "PARTIALLY_REVERSED"
	}
	if err := tx.Save(&transfer).Error; err != nil {
		tx.Rollback()
		return nil, err
	}

	if err := tx.Commit().Error; err != nil {
		tx.Rollback()
		return nil, err
	}
	return &transfer, nil
}

// CompleteTopUpToWallet credits a wallet account after a successful topup.
// Must be called within the same DB transaction as the topup completion.
// The legacy User.Quota field is still updated by the existing recharge functions;
// this adds the wallet-layer entry for unified balance tracking.
func CompleteTopUpToWallet(tx *gorm.DB, userID int64, amountCents int64, tradeNo string) error {
	ownerID := fmt.Sprintf("%d", userID)
	wallet, err := lockWallet(tx, "USER", ownerID)
	if err != nil {
		return err
	}

	wallet.AvailableCents += amountCents
	if err := tx.Save(wallet).Error; err != nil {
		return err
	}

	// Record wallet transfer
	transferNo := fmt.Sprintf("WT-TOPUP-%s", tradeNo)
	transfer := WalletTransfer{
		TransferNo:      transferNo,
		BusinessType:    "TOPUP",
		BusinessOrderNo: tradeNo,
		TransferType:    "TOPUP",
		IdempotencyKey:  fmt.Sprintf("topup:%s", tradeNo),
		FromOwnerType:   "PLATFORM",
		FromOwnerID:     "platform",
		ToOwnerType:     "USER",
		ToOwnerID:       ownerID,
		AmountCents:     amountCents,
		Currency:        "CNY",
		Status:          "SUCCEEDED",
	}
	if err := tx.Create(&transfer).Error; err != nil {
		// If duplicate (idempotent), ignore
		if errors.Is(err, gorm.ErrDuplicatedKey) {
			return nil
		}
		return err
	}

	// Double-entry ledger
	appendLedger(tx, transferNo, "PLATFORM", "platform", "DEBIT", amountCents, 0) // platform clearing
	appendLedger(tx, transferNo, "USER", ownerID, "CREDIT", amountCents, wallet.AvailableCents)

	return nil
}

// GetWalletLedger returns recent ledger entries for an owner.
func GetWalletLedger(ownerType, ownerID string, limit int) ([]LedgerEntryView, error) {
	var entries []WalletLedgerEntry
	err := DB.Where("owner_type = ? AND owner_id = ?", ownerType, ownerID).
		Order("id DESC").Limit(limit).Find(&entries).Error
	if err != nil {
		return nil, err
	}

	views := make([]LedgerEntryView, len(entries))
	for i, e := range entries {
		views[i] = LedgerEntryView{
			TransferNo:  e.TransferNo,
			EntryType:   e.EntryType,
			AmountCents: e.AmountCents,
			BalanceAfter: e.BalanceAfter,
			CreatedAt:   e.CreatedAt,
		}
	}
	return views, nil
}
