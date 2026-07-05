package controller

import (
	"net/http"

	"github.com/QuantumNous/new-api/model"
	"github.com/gin-gonic/gin"
)

// -- Request/Response types --

type OwnerRef struct {
	Type string `json:"type" binding:"required,oneof=USER WORKSPACE PLATFORM"`
	ID   string `json:"id" binding:"required"`
}

type PurchaseTransferRequest struct {
	BusinessOrderNo string  `json:"business_order_no" binding:"required"`
	Buyer           OwnerRef `json:"buyer" binding:"required"`
	Seller          OwnerRef `json:"seller" binding:"required"`
	AmountCents     int64   `json:"amount_cents" binding:"required,gt=0"`
	PlatformFeeCents int64  `json:"platform_fee_cents" binding:"min=0"`
	Currency        string  `json:"currency" binding:"required"`
	IdempotencyKey  string  `json:"idempotency_key" binding:"required"`
	ActorUserID     int64   `json:"actor_user_id"`
}

type TransferResult struct {
	TransferNo   string `json:"transfer_no"`
	Status       string `json:"status"`
	BuyerBalance int64  `json:"buyer_balance_after"`
}

type ReleaseRequest struct {
	IdempotencyKey string `json:"idempotency_key" binding:"required"`
}

type ReverseRequest struct {
	AmountCents    int64  `json:"amount_cents" binding:"required,gt=0"`
	IdempotencyKey string `json:"idempotency_key" binding:"required"`
}

type WalletBalanceResponse struct {
	OwnerType      string `json:"owner_type"`
	OwnerID        string `json:"owner_id"`
	AvailableCents int64  `json:"available_cents"`
	FrozenCents    int64  `json:"frozen_cents"`
	Currency       string `json:"currency"`
}

type PrecheckRequest struct {
	Owner      OwnerRef `json:"owner" binding:"required"`
	AmountCents int64   `json:"amount_cents" binding:"required,min=0"`
	Permission string  `json:"permission"`
}

// -- Handlers --

// GetWalletBalance returns available and frozen balance for an owner.
func GetAicpWalletBalance(c *gin.Context) {
	ownerType := c.Param("ownerType")
	ownerID := c.Param("ownerId")

	available, frozen, err := model.GetWalletBalance(ownerType, ownerID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"data": WalletBalanceResponse{
			OwnerType:      ownerType,
			OwnerID:        ownerID,
			AvailableCents: available,
			FrozenCents:    frozen,
			Currency:       "CNY",
		},
	})
}

// AicpWalletPrecheck validates wallet existence, balance, and permissions.
func AicpWalletPrecheck(c *gin.Context) {
	var req PrecheckRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": err.Error()})
		return
	}

	err := model.PrecheckWallet(req.Owner.Type, req.Owner.ID, req.AmountCents, req.Permission)
	if err != nil {
		c.JSON(http.StatusUnprocessableEntity, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": gin.H{"allowed": true}})
}

// AicpWalletPurchase executes an atomic purchase transfer (debit buyer, credit seller frozen, credit platform).
func AicpWalletPurchase(c *gin.Context) {
	var req PurchaseTransferRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": err.Error()})
		return
	}

	params := model.PurchaseTransferParams{
		BusinessOrderNo:  req.BusinessOrderNo,
		BuyerType:        req.Buyer.Type,
		BuyerID:          req.Buyer.ID,
		SellerType:       req.Seller.Type,
		SellerID:         req.Seller.ID,
		AmountCents:      req.AmountCents,
		PlatformFeeCents: req.PlatformFeeCents,
		Currency:         req.Currency,
		IdempotencyKey:   req.IdempotencyKey,
	}
	result, err := model.ExecutePurchaseTransfer(params)
	if err != nil {
		if err == model.ErrIdempotencyConflict {
			c.JSON(http.StatusConflict, gin.H{"success": false, "message": err.Error()})
			return
		}
		if err == model.ErrInsufficientBalance {
			c.JSON(http.StatusUnprocessableEntity, gin.H{"success": false, "message": err.Error()})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": result})
}

// AicpWalletFindByBusinessOrder looks up a transfer by business order number.
func AicpWalletFindByBusinessOrder(c *gin.Context) {
	orderNo := c.Param("orderNo")

	result, err := model.FindTransferByBusinessOrder(orderNo)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": result})
}

// AicpWalletRelease releases frozen seller funds to available.
func AicpWalletRelease(c *gin.Context) {
	transferNo := c.Param("transferNo")
	var req ReleaseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": err.Error()})
		return
	}

	result, err := model.ReleaseTransfer(transferNo, req.IdempotencyKey)
	if err != nil {
		c.JSON(http.StatusUnprocessableEntity, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": result})
}

// AicpWalletReverse reverses a transfer (full or partial refund).
func AicpWalletReverse(c *gin.Context) {
	transferNo := c.Param("transferNo")
	var req ReverseRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"success": false, "message": err.Error()})
		return
	}

	result, err := model.ReverseTransfer(transferNo, req.AmountCents, req.IdempotencyKey)
	if err != nil {
		c.JSON(http.StatusUnprocessableEntity, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": result})
}

// AicpWalletLedger returns ledger entries for an owner.
func AicpWalletLedger(c *gin.Context) {
	ownerType := c.Param("ownerType")
	ownerID := c.Param("ownerId")

	entries, err := model.GetWalletLedger(ownerType, ownerID, 50)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"success": false, "message": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"success": true, "data": entries})
}
